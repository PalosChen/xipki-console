# XiPKI Console

[English](README.md) | **简体中文**

XiPKI Console 是 [XiPKI](https://github.com/xipki/xipki) 的 Web 管理界面。
React 前端支持英文和简体中文；Java 25 后端使用嵌入式 Tomcat 和 JDBC，
通过双向 TLS 调用 CA 管理接口，并使用 XiPKI 工具模块处理配置和 JSON 编码。

## 功能

- 根 CA、中间 CA 创建，外部 CA 导入，证书链与 CA 配置管理。
- 证书签发、下载、挂起、吊销、CRL 管理及重新发布。
- 证书模板、signer、requestor、publisher、keypair generator 及 CA 关联管理。
- 用户和角色编辑、操作权限、CA/模板授权范围与审计记录。
- 个人账户设置、密码修改及中英文界面切换。

Web 账户、权限、会话和审计数据存储在 MariaDB 中。CA 数据库独立配置，
可使用 H2 或外部数据库。

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `console-server/` | Java 后端、数据库迁移与回归测试 |
| `console-ui/` | React 应用、翻译资源与前端测试 |
| `tests/api/` | 针对运行中系统手动执行的 API 验收脚本 |
| `scripts/` | 编译、压缩包打包与本地启动脚本 |
| `deploy/` | 运行镜像、Compose 部署与启动脚本 |
| `guides/` | 正式维护的英文配置、权限、测试及国际化指南 |

## 构建

需要 JDK 25+、Maven、Node.js 22.16+ 和 npm。在仓库根目录执行：

```sh
export XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki
sh scripts/build.sh
```

后端依赖 `console-server/pom.xml` 中声明的 XiPKI `7.0.0-SNAPSHOT` 模块。
设置 `XIPKI_SOURCE_DIR` 后，构建脚本会从该源码安装匹配的 `util` 模块及其依赖；
如果本地 Maven 仓库已有构建产物，可不设置该变量。脚本按锁文件安装前端依赖，
运行前后端测试并完成构建。执行 `sh scripts/package.sh` 可生成主机运行压缩包，
产物位于 `dist/`。

XiPKI 源码版本须与 `console-server/pom.xml` 中的版本兼容。使用其他版本前
需要验证兼容性。

运行文件包括 `console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar`
和完整的 `console-server/target/lib/`。按照[运行配置](guides/configuration.md)设置环境后，在主机执行：

```sh
java -Dsun.net.client.defaultConnectTimeout=3000 -Dsun.net.client.defaultReadTimeout=3000 \
  -jar console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar
```

前端开发时，在 `console-ui/` 执行 `npm run dev`。Vite 监听 5173 端口，默认将 `/api`
代理到 `http://localhost:8080`；后端地址不同时设置 `VITE_API_TARGET`。

## Docker 部署

`sh scripts/build.sh --docker` 构建 Console 和数据库镜像。Console 镜像
在 Alpine 上打包已构建的应用和 Java 25；数据库镜像使用 Alpine 上的
MariaDB 11.4.13。镜像验证要求见[安全指南](guides/security.md)。

`deploy/compose.yml` 启动 Console、MariaDB 和独立 CA 镜像，并使用命名卷持久化数据。
需要**匹配的 XiPKI Docker 发行镜像**，该镜像必须将管理客户端导出到 `/export/web`，
并在 `https://ca:8443/ca/mgmt` 提供管理接口。CA 镜像必须满足这一初始化约定。

CA 镜像由独立的 XiPKI 发行项目维护，本仓库不发布 CA 镜像。当前 XiPKI
上游主分支已不再包含历史路径 `docker/ca/Dockerfile`，因此必须单独提供满足
`/export/web` 和 `https://ca:8443/ca/mgmt` 约定的 CA 镜像。正式部署应固定经过审查的
镜像 digest 以保证可复现。在已启动 Docker 的开发主机上构建并扫描 Console 和数据库镜像，
然后启动：

```sh
export XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki
sh scripts/build.sh --docker
export CA_IMAGE=your-xipki-7-ca-image
export WEB_IMAGE=xipki-console:local
export DB_IMAGE=xipki-console-db:local
sh scripts/check-security.sh
export WEB_PORT=28127
sh scripts/local.sh up
```

示例使用本地镜像标签。安全检查需要 Trivy；正式部署应使用经过审查的
镜像 digest。如果构建主机与 Docker 宿主机不同，需先传输镜像。

脚本初始化缺失的凭据，启动独立的 `xipki-console-local` Compose 项目，等待三个服务健康，
并检查是否出现反复重启。提示就绪后，其他设备可访问 `http://<宿主机内网 IP>:28127`。
`WEB_BIND_ADDRESS` 默认是 `0.0.0.0`；设为 `127.0.0.1` 可限制为仅宿主机访问。
需要支持 `--wait-timeout` 的 Compose v2，默认启动超时为 300 秒。

日常操作使用 `sh scripts/local.sh status`、`sh scripts/local.sh logs` 或
`sh scripts/local.sh stop`。停止会保留所有数据，下次 `up` 继续使用。
这些脚本读取已导出的环境变量，不加载 `deploy/.env`。
主机启动、离线构建和压缩包打包说明见[脚本指南](scripts/README.md)。

初始用户名为 `admin`，初始密码在宿主机的 `deploy/secrets/admin-password` 中，首次登录必须修改。
初始化会保留已有凭据及已初始化的 Web 数据库。请一并备份数据库卷、托管密钥及其加密密钥。
通过 HTTPS 对外提供服务前，请阅读[运行配置](guides/configuration.md)。

## 文档

以下正式指南以英文维护：

- [运行配置与部署](guides/configuration.md)
- [编译、打包与本地启动](scripts/README.md)
- [用户、角色与权限](guides/permissions.md)
- [测试](guides/testing.md)
- [新增界面语言](guides/internationalization.md)

## 参与开发

源码、API 消息及正式指南以英文编写；界面翻译放在语言资源中。
两份 README 应保持内容一致。提交修改前运行[测试说明](guides/testing.md)中的检查。

## 许可证

XiPKI Console 采用 [Apache License 2.0](LICENSE)。版权声明见 [NOTICE](NOTICE)。
第三方依赖遵循其各自的许可证。
