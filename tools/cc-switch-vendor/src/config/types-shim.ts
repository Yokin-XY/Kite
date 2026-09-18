// Kite 转换用类型 shim：只声明 ProviderCategory（上游 @/types 的最小子集）
export type ProviderCategory =
  | "official"
  | "cn_official"
  | "third_party"
  | "aggregator"
  | "cloud_provider"
  | "custom";
