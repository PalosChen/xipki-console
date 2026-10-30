# XiPKI Console

[English](README.md) | **简体中文**

轻量级 XiPKI Web 管理平台，提供 CA 与证书管理、基于角色的访问控制、审计日志及 Docker 部署能力。

本项目是独立开发的 [XiPKI](https://github.com/xipki/xipki) 配套管理平台。
前端采用 React，支持英文和简体中文；后端基于 Java 17，使用嵌入式 Tomcat、直接 JDBC、
Console 实现的 XiPKI CA 管理协议适配层，以及 XiPKI 的 JSON 和配置工具，
无需 Spring Boot、Jackson、ORM 或连接池。

## 功能

- 根 CA、中间 CA 创建，外部 CA 导入，证书链与 CA 配置管理。
- 证书签发、下载、挂起、吊销、CRL 管理及重新发布。
- 证书模板、signer、requestor、publisher、keypair generator 及 CA 关联管理。
- 用户和角色编辑、操作权限、CA/模板授权范围与审计记录。
- 个人账户设置、密码修改及中英文界面切换。

Web 账户、权限、会话和审计数据使用 MariaDB。CA 使用独立的数据库配置，可采用 H2 或外部数据库。

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `console-server/` | 轻量后端、数据库迁移与回归测试 |
| `console-ui/` | React 应用、翻译资源与前端测试 |
| `tests/api/` | 针对运行中系统手动执行的 API 验收脚本 |
| `scripts/` | 编译、压缩包打包与本地启动脚本 |
| `deploy/` | 运行镜像、Compose 部署与启动脚本 |
| `guides/` | 正式维护的英文配置、权限、测试及国际化指南 |

`docs/` 用于本地 AI 分析、草稿和过程记录，整体忽略提交，也不包含在发布包中。
需要长期维护的正式说明放在 `guides/`。

## 在开发主机编译

需要 JDK 17+、Maven、Node.js 22.16+ 和 npm。在仓库根目录执行：

```sh
sh scripts/build.sh
```

脚本按锁文件安装前端依赖，执行前端测试和构建，然后测试并打包轻量后端。
如需同时生成主机运行压缩包，使用 `sh scripts/package.sh`，产物保存在已忽略的 `dist/` 目录。

当前只复用 **XiPKI `6.6.0-SNAPSHOT` 工具模块**中的 JSON 和配置编码能力。
CA 管理协议与密码学处理使用 Console 适配层和 JDK 提供者，Console 运行时不包含
Bouncy Castle 或 XiPKI security 模块。需要先安装匹配的工具模块构建产物。
也可以显式指定源码目录，将此步骤纳入构建：

```sh
XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki sh scripts/build.sh
```

请使用匹配的现有 6.x 源码。上游 `master` 的 7.x 开发线使用不同的 Java 包及 Maven 坐标，
仅修改 `xipki.version` 不能完成升级。其他 XiPKI 版本的兼容性需要单独验证。

运行文件包括 `console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar`
和完整的 `console-server/target/lib/`。按照[运行配置](guides/configuration.md)设置环境后，在主机执行：

```sh
java -Dsun.net.client.defaultConnectTimeout=3000 -Dsun.net.client.defaultReadTimeout=3000 \
  -jar console-server/target/xipki-console-server-1.0.0-SNAPSHOT.jar
```

前端开发时，在 `console-ui/` 执行 `npm run dev`。Vite 监听 5173 端口，默认将 `/api`
代理到 `http://localhost:8080`；后端地址不同时设置 `VITE_API_TARGET`。

## Docker 部署

Console 的 `deploy/Dockerfile` 只打包编译产物，不执行编译和测试。
其构建上下文排除了源码、凭据和本地运行数据。
`--docker` 同时构建 Console 和数据库镜像。Console 使用官方 Alpine 的 Java 17 headless；
数据库使用 Alpine 中的 MariaDB 11.4.13，并用 `su-exec` 替换上游基于旧 Go 的降权辅助程序。
发布前必须执行[安全检查](guides/security.md)，不能仅凭升级镜像标签判断安全。

`deploy/compose.yml` 启动 Console、MariaDB 和独立 CA 镜像，并使用命名卷持久化数据。
需要**匹配的 XiPKI Docker 发行镜像**，该镜像必须将管理客户端导出到 `/export/web`，
并在 `https://ca:8443/ca/mgmt` 提供管理接口。任意上游 CA 镜像不一定满足此初始化约定。

CA 镜像需要从匹配的 XiPKI `6.6.0-SNAPSHOT` 发行源码单独构建，该源码须包含
`docker/ca/Dockerfile`。本仓库不发布 CA 镜像。将 XiPKI 源码固定到经过测试的提交，
并使用同一份源码构建 Console 所需的工具模块。在已启动 Docker 的开发主机上
构建本地镜像，完成扫描后启动：

```sh
export XIPKI_SOURCE_DIR=/absolute/path/to/matching/xipki
sh scripts/build.sh --docker
docker build -f "$XIPKI_SOURCE_DIR/docker/ca/Dockerfile" \
  -t xipki-ca:security-update "$XIPKI_SOURCE_DIR"
export CA_IMAGE=xipki-ca:security-update
export WEB_IMAGE=xipki-console:local
export DB_IMAGE=xipki-console-db:local
sh scripts/check-security.sh
export WEB_PORT=28127
sh scripts/local.sh up
```

`security-update` 只是本地开发标签，并非已发布镜像，也不能证明镜像安全。
扫描需要 Trivy；正式部署应使用经过审查的不可变镜像 digest。如果构建主机
与 Docker 宿主机不同，先传输或发布镜像，并选择空闲端口。

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

源码、标识符、注释、日志、API 消息及正式指南统一使用英文。
`README.md` 为主要英文入口；内容变化时同步更新 `README.zh-CN.md`，两者顶部提供互相跳转的链接。

界面翻译放在语言资源中。语言名称、Unicode 及翻译测试数据可以保留对应语言的原始字符。
提交修改前请参考[测试说明](guides/testing.md)。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。第三方依赖遵循其各自的许可证。
