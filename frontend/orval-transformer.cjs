const COMPONENT_PREFIX = '#/components/schemas/';
const RESULT_PREFIX = '#/components/schemas/Result';
const WILDCARD_MEDIA = '*/*';
const JSON_MEDIA = 'application/json';

function cloneSchema(schema) {
  return JSON.parse(JSON.stringify(schema));
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

      Object.values(responses).forEach((response) => {
        const content = response?.content;
        if (!content || typeof content !== 'object') {
          return;
        }

        normalizeMediaType(content);

        Object.values(content).forEach((mediaType) => {
          if (!mediaType || typeof mediaType !== 'object') {
            return;
          }
          mediaType.schema = unwrapResultDataSchema(openApi, mediaType.schema);
        });
      });
    });
  });

  return openApi;
};
