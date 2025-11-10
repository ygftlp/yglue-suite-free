export const modelItems = [
  { fqcn: "com.demo.UserRegisterRequest", title: "注册请求模型", version: "1.1.0" },
  { fqcn: "com.demo.CheckoutCtx", title: "结算上下文", version: "1.0.0" },
] as const

export const modelOptions = modelItems.map((item) => ({
  label: item.title,
  value: item.fqcn,
}))
