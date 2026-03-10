import { defineConfig } from 'orval';

export default defineConfig({
  movieApi: {
    // 数据源：本地后端暴露的 OpenAPI JSON 地址
    input: {
      target: 'http://localhost:8080/v3/api-docs',
      // 生成前做一次 OpenAPI 规范修正，减少后端细节对前端生成结果的影响
      override: {
        transformer: './orval-transformer.cjs',
      },
    },

    output: {
      // 客户端类型：生成 Vue Query 的 Hooks (例如 useGetMovieList)
      client: 'vue-query',
      // 使用 Axios 作为底层 HTTP 客户端，便于统一参数序列化策略
      httpClient: 'axios',
      // 生成模式：按 Swagger 的 tags 将 API 拆分到不同的文件中
      mode: 'tags-split',

      // 将 API 请求函数生成到单独的 endpoints 文件夹
      target: './src/api/endpoints',
      // 将所有实体类/类型定义 (如 Movie, UserVO) 独立抽离到 model 文件夹，方便在 Vue 组件中直接 import 复用
      schemas: './src/api/model',

      // 命名规范：统一驼峰命名
      namingConvention: 'camelCase',
      // 每次生成前清空旧文件，避免接口删除后前端残留无用代码
      clean: true,

      // 生成统一的入口文件
      indexFiles: true,
      
      override: {
        // 接入自定义 Axios 实例
        mutator: {
          path: './src/api/mutator/custom-instance.ts',
          name: 'customInstance',
        },
        // 统一查询参数序列化：过滤 null / undefined，避免生成 null 字符串
        paramsSerializer: {
          path: './src/api/mutator/custom-params-serializer.ts',
          name: 'customParamsSerializer',
        },

        // Vue Query 配置
        query: {
          useQuery: true,

          // 开启请求取消支持：配合 custom-instance 里的 AbortController
          signal: true,
        },
      },
    },
    hooks: {
      afterAllFilesWrite:
        'sh -c \'if [ -x ./node_modules/.bin/prettier ]; then ./node_modules/.bin/prettier --write ./src/api/**/*.ts; fi\'',
    },
  },
});
