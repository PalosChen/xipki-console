import {spawn} from 'node:child_process'
import {randomBytes} from 'node:crypto'
import {mkdtemp, rm, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {setTimeout as delay} from 'node:timers/promises'

const id = randomBytes(6).toString('hex')
const fresh = `xipki-mariadb-fresh-${id}`
const original = `xipki-mariadb-original-${id}`
const upgraded = `xipki-mariadb-upgraded-${id}`
const volume = `xipki-mariadb-volume-${id}`
const candidateImage = process.env.DB_IMAGE || 'xipki-console-db:local'
const originalImage = process.env.DB_BASELINE_IMAGE ||
  'mariadb:11.4.13@sha256:d1891ede5a559f1086f55053bb8cc1569b0298338c4d72d71725a7f74f6ba620'
const rootPassword = randomBytes(24).toString('hex')
const appPassword = randomBytes(24).toString('hex')
const secrets = await mkdtemp(join(tmpdir(), 'xipki-mariadb-probe-'))

async function command(args, env = process.env) {
  return new Promise((resolve, reject) => {
    const child = spawn('docker', args, {env, stdio: ['ignore', 'pipe', 'pipe']})
    let output = ''
    child.stdout.on('data', chunk => output += chunk)
    child.stderr.on('data', chunk => output += chunk)
    child.on('error', reject)
    child.on('close', code => resolve({code, output}))
  })
}

async function docker(...args) {
  const result = await command(args)
  if (result.code !== 0) throw new Error(`Docker ${args[0]} failed: ${result.output}`)
  return result.output.trim()
}

async function ready(name) {
  const deadline = performance.now() + 90_000
  while (performance.now() < deadline) {
    const state = await docker('inspect', '--format', '{{.State.Status}} {{.State.Restarting}}', name)
    if (state !== 'running false') throw new Error(`${name} stopped during initialization`)
    if ((await command(['exec', name, 'healthcheck.sh', '--connect', '--innodb_initialized'])).code === 0) return
    await delay(1000)
  }
  throw new Error(`${name} did not become healthy`)
}

async function start(name, image, storage, filePasswords) {
  const args = ['run', '-d', '--name', name, '--tmpfs', '/run/mysqld:rw,size=16m',
    '-p', '127.0.0.1::3306']
  if (storage === 'tmpfs') args.push('--tmpfs', '/var/lib/mysql:rw,size=512m')
  else args.push('-v', `${volume}:/var/lib/mysql`)
  if (filePasswords) {
    args.push('-v', `${secrets}:/run/probe-secrets:ro`)
    args.push('-e', 'MARIADB_ROOT_PASSWORD_FILE=/run/probe-secrets/root')
    args.push('-e', 'MARIADB_PASSWORD_FILE=/run/probe-secrets/app')
  } else {
    args.push('-e', 'MARIADB_ROOT_PASSWORD', '-e', 'MARIADB_PASSWORD')
  }
  args.push('-e', 'MARIADB_DATABASE=xipki_web_lite_tests', '-e', 'MARIADB_USER=xipki_web', image)
  const env = {...process.env, MARIADB_ROOT_PASSWORD: rootPassword, MARIADB_PASSWORD: appPassword}
  const result = await command(args, env)
  if (result.code !== 0) throw new Error(`Docker run failed: ${result.output}`)
  await ready(name)
}

async function backendTests(name) {
  const port = (await docker('port', name, '3306/tcp')).split(':').at(-1)
  if (!/^\d+$/.test(port)) throw new Error('Invalid disposable database port')
  const result = await new Promise((resolve, reject) => {
    const env = {...process.env,
      WEB_LITE_TEST_DB_URL: `jdbc:mariadb://127.0.0.1:${port}/xipki_web_lite_tests`,
      WEB_LITE_TEST_DB_USER: 'root', WEB_LITE_TEST_DB_PASSWORD: rootPassword}
    const child = spawn('mvn', ['-o', '-f', 'console-server/pom.xml', '-Dlite.db.required=true',
      'test', '-q'], {env, stdio: ['ignore', 'pipe', 'pipe']})
    let output = ''
    child.stdout.on('data', chunk => output += chunk)
    child.stderr.on('data', chunk => output += chunk)
    child.on('error', reject)
    child.on('close', code => resolve({code, output}))
  })
  if (result.code !== 0) throw new Error(`Backend tests failed:\n${result.output.slice(-5000)}`)
  console.log('Full backend test suite passed against candidate MariaDB image')
}

async function query(name, sql) {
  const result = await command(['exec', '-e', 'MYSQL_PWD', name, 'mariadb', '--protocol=socket',
    '-uxipki_web', '-N', '-B', 'xipki_web_lite_tests', '-e', sql], {...process.env, MYSQL_PWD: appPassword})
  if (result.code !== 0) throw new Error(`Database query failed: ${result.output}`)
  return result.output.trim()
}

try {
  await writeFile(join(secrets, 'root'), rootPassword, {mode: 0o600})
  await writeFile(join(secrets, 'app'), appPassword, {mode: 0o600})
  await start(fresh, candidateImage, 'tmpfs', true)
  if (await query(fresh, 'SELECT 1') !== '1') throw new Error('Fresh database user cannot query')
  console.log('Fresh initialization and password-file authentication passed')
  await backendTests(fresh)
  await docker('stop', fresh)
  await docker('volume', 'create', volume)
  await start(original, originalImage, 'volume', false)
  await query(original, 'CREATE TABLE migration_probe(value VARCHAR(64) NOT NULL)')
  await query(original, "INSERT INTO migration_probe(value) VALUES ('persisted')")
  await docker('stop', original)
  await start(upgraded, candidateImage, 'volume', true)
  if (await query(upgraded, 'SELECT value FROM migration_probe') !== 'persisted') {
    throw new Error('Existing MariaDB data did not survive the image change')
  }
  console.log('Existing official-image data volume passed candidate restart and readback')
} catch (error) {
  console.error(error.message)
  for (const name of [fresh, original, upgraded]) {
    const result = await command(['logs', name])
    if (result.code === 0) console.error(`${name} logs:\n${result.output.split('\n').slice(-45).join('\n')}`)
  }
  process.exitCode = 1
} finally {
  let cleanupFailed = false
  for (const name of [fresh, original, upgraded]) {
    const result = await command(['rm', '-f', name])
    if (result.code !== 0 && !result.output.includes('No such container')) {
      console.error(`Could not remove disposable container ${name}: ${result.output}`)
      cleanupFailed = true
    }
  }
  const result = await command(['volume', 'rm', volume])
  if (result.code !== 0 && !result.output.includes('no such volume')) {
    console.error(`Could not remove disposable volume ${volume}: ${result.output}`)
    cleanupFailed = true
  }
  try { await rm(secrets, {recursive: true, force: true}) }
  catch (error) { console.error(`Could not remove disposable passwords: ${error.message}`); cleanupFailed = true }
  if (cleanupFailed) process.exitCode = 1
  else console.log('Disposable containers, volume and passwords removed')
}
