# API 统一响应格式规范（Result\<T\>）

本文档规范了 BankAgent 平台全部后端接口的响应格式，以及前端如何消费这些响应。**所有新增 API 必须遵守本格式。**

---

## 1. 后端规范

### 1.1 Result\<T\> 结构

四个微服务（user-service / task-service / tool-agent / code-agent）各有一份完全一致的 `Result<T>` 类，位于各服务 `dto/Result.java`：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `Integer` | `200` 成功；`401` Token 过期；`500` 业务或系统错误 |
| `message` | `String` | 人类可读的描述信息 |
| `data` | `T` (泛型) | 实际响应数据，可以为 `null` |

### 1.2 工厂方法

```java
Result.success(data)           // code=200, message="success"
Result.error("错误描述")        // code=500
Result.error(401, "Token过期")  // 自定义 code
```

### 1.3 Controller 返回规范

```java
@GetMapping("/list")
public Result<List<Xxx>> list() {
    return Result.success(service.findAll());
}

@PostMapping("/submit")
public Result<String> submit(@RequestBody XxxRequest req) {
    if (invalid) {
        return Result.error("参数校验失败");
    }
    return Result.success("操作成功");
}
```

### 1.4 异常兜底

各服务通过 GlobalExceptionHandler 兜底未捕获异常，返回 `Result.error(e.getMessage())`，避免敏感信息泄露到响应体。

---

## 2. 前端消费规范

### 2.1 Axios 拦截器自动解包

文件：[web-ui/src/utils/request.ts](../web-ui/src/utils/request.ts)

```
后端返回 { code: 200, message: "success", data: {...} }
                    ↓ 拦截器自动解包
Vue 组件拿到 { ... }
```

规则：
- 如果响应体有 `code` 字段：拦截器取 `res.data` 返回，业务代码直接拿到数据
- 如果响应体无 `code` 字段：拦截器返回原始 payload（兼容非 Result 格式的接口，如 code-agent Python 服务）

### 2.2 错误处理

| 场景 | 前端行为 |
|------|----------|
| `code === 401`（应用内） | 静默清除 Token，跳转 `/login?expired=1` |
| `code === 401`（公开页面） | 显示错误提示（如密码错误） |
| `code === 500` | 显示 `ElMessage.error` |
| HTTP 状态码 401 | 同 code 401 处理 |
| 网络异常 | 显示网络错误提示 |

### 2.3 调用示例

```typescript
// API 定义（web-ui/src/api/xxx.ts）
export const getList = () => request.get('/user/list')

// 组件中使用 —— res 直接就是 data 字段的内容
const res = await getList()
// res 就是 List<Xxx>，不用再 .data
```

---

## 3. 注意事项

1. **不要手动再包一层**。Controller 返回 `Result<T>` 就行，Spring MVC 自动序列化。
2. **不要用 HTTP 状态码区分业务错误**。除非是 401 认证错误，其余业务错误统一 `code=500`，靠 `message` 区分。
3. **新增服务必须复制 `Result<T>` 类**。目前四个服务各自维护了一份（因为模块间没有共同的 common 包），新增服务时从现有服务复制即可。
4. **前端接新接口时注意**：如果后端返回的是非 Result 格式（如 Python 推理服务的裸 JSON），前端拦截器已兼容，无需特殊处理。
