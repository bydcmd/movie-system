export type UnwrapResult<T> = T extends { data: infer D } ? D : never;
