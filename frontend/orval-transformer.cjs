const COMPONENT_PREFIX = '#/components/schemas/';
const RESULT_PREFIX = '#/components/schemas/Result';
const WILDCARD_MEDIA = '*/*';
const JSON_MEDIA = 'application/json';
const SUCCESS_STATUS_PATTERN = /^2\d\d$/;

const OPERATION_DATA_SCHEMA_MAP = {
  cancelAccount: { type: 'string' },
  changePassword: { type: 'string' },
  getCurrentUserInfo: 'UserVO',
  getMyProfile: 'UserProfileVO',
  getPublicUserInfo: 'PublicUserVO',
  login: 'UserVO',
  logout: { type: 'string' },
  refreshToken: { type: 'string' },
  register: { type: 'string' },
  updateAvatar: { type: 'string' },
  updateMyProfile: { type: 'string' },
};

function cloneSchema(schema) {
  return JSON.parse(JSON.stringify(schema));
}

function isSuccessStatus(statusCode) {
  return SUCCESS_STATUS_PATTERN.test(String(statusCode));
}

function isResultSchema(schema) {
  return Boolean(schema?.$ref && schema.$ref.startsWith(RESULT_PREFIX));
}

function unwrapResultDataSchema(openApi, schema) {
  if (!schema || typeof schema !== 'object') {
    return schema;
  }

  const ref = schema.$ref;
  if (!ref || !ref.startsWith(RESULT_PREFIX)) {
    return schema;
  }

  const schemaName = ref.slice(COMPONENT_PREFIX.length);
  const resultSchema = openApi?.components?.schemas?.[schemaName];
  const dataSchema = resultSchema?.properties?.data;

  if (!dataSchema || typeof dataSchema !== 'object') {
    return schema;
  }

  return cloneSchema(dataSchema);
}

function getOperationDataSchema(openApi, operationId) {
  const mappedSchema = OPERATION_DATA_SCHEMA_MAP[operationId];
  if (!mappedSchema) {
    return undefined;
  }

  if (typeof mappedSchema === 'string') {
    const schemaName = mappedSchema;
    if (openApi?.components?.schemas?.[schemaName]) {
      return { $ref: `${COMPONENT_PREFIX}${schemaName}` };
    }
    return undefined;
  }

  return cloneSchema(mappedSchema);
}

function normalizeMediaType(content) {
  if (!content || typeof content !== 'object') {
    return;
  }

  if (content[WILDCARD_MEDIA] && !content[JSON_MEDIA]) {
    content[JSON_MEDIA] = content[WILDCARD_MEDIA];
  }
  delete content[WILDCARD_MEDIA];
}

module.exports = (openApi) => {
  const paths = openApi?.paths || {};

  Object.values(paths).forEach((pathItem) => {
    if (!pathItem || typeof pathItem !== 'object') {
      return;
    }

    Object.values(pathItem).forEach((operation) => {
      const responses = operation?.responses;
      if (!responses || typeof responses !== 'object') {
        return;
      }

      Object.entries(responses).forEach(([statusCode, response]) => {
        const content = response?.content;
        if (!content || typeof content !== 'object') {
          return;
        }

        normalizeMediaType(content);

        Object.values(content).forEach((mediaType) => {
          if (!mediaType || typeof mediaType !== 'object') {
            return;
          }

          if (isSuccessStatus(statusCode) && isResultSchema(mediaType.schema)) {
            const operationDataSchema = getOperationDataSchema(
              openApi,
              operation?.operationId,
            );
            if (operationDataSchema) {
              mediaType.schema = operationDataSchema;
              return;
            }
          }

          mediaType.schema = unwrapResultDataSchema(openApi, mediaType.schema);
        });
      });
    });
  });

  return openApi;
};
