# 智能骑行 · Cloudflare 中控

骑行结束后,App 会自动把本次骑行(汇总数据 + 轨迹点)POST 到这个 Cloudflare Worker,并写入 D1 数据库;打开 Worker 首页即可在网页表格里查看所有记录。

## 一、前提

```bash
npm i -g wrangler
wrangler login
```

## 二、创建 D1 数据库

```bash
cd cloudflare
wrangler d1 create smart_cycling
```

把输出的 `database_id` 填入 `wrangler.toml` 的 `database_id`。

初始化表结构(也可不停,Worker 首次请求会自动建表):

```bash
wrangler d1 execute smart_cycling --file=./schema.sql --remote
```

## 三、设置访问令牌(推荐)

```bash
wrangler secret put SYNC_TOKEN
# 输入一个自定义密码,例如 my-cycling-2026
```

> 若不设置 SYNC_TOKEN,接口不鉴权(方便快速试用,但任何人都能读写,不建议用于正式)。

## 四、部署

```bash
wrangler deploy
```

部署后会得到一个地址,例如:
`https://smart-cycling-console.<你的子域>.workers.dev`

- 浏览器打开该地址 → 中控网页,右上角填入 SYNC_TOKEN 后即可查看记录。

## 五、让 App 指向中控

把上面的地址与令牌填到项目根目录 `gradle.properties`(或构建时传参 / GitHub Secret):

```properties
CLOUD_SYNC_URL=https://smart-cycling-console.xxx.workers.dev
CLOUD_SYNC_TOKEN=my-cycling-2026
```

重新构建 APK 即生效。若两项留空,App 会自动跳过云端上传(不影响本地记录)。

## 接口说明

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 中控网页 |
| POST | `/api/rides` | 上传一次骑行(App 自动调用),需 `Authorization: Bearer <token>` |
| GET | `/api/rides` | 骑行列表 |
| GET | `/api/rides/{id}` | 单次详情 + 轨迹点 |
