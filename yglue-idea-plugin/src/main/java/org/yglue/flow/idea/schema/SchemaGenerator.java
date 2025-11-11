package org.yglue.flow.idea.schema;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.JavaPsiFacade;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.*;

/**
 * 负责根据 Psi 信息生成请求入参的 JSON Schema。
 */
public class SchemaGenerator {

    private static final String JSON_SCHEMA_DRAFT_7 = "http://json-schema.org/draft-07/schema#";

    private static final Set<String> NOT_NULL_ANNOTATIONS = Set.of(
            "jakarta.validation.constraints.NotNull",
            "jakarta.validation.constraints.NotEmpty",
            "jakarta.validation.constraints.NotBlank",
            "javax.validation.constraints.NotNull",
            "javax.validation.constraints.NotEmpty",
            "javax.validation.constraints.NotBlank"
    );

    private static final Set<String> SIZE_ANNOTATIONS = Set.of(
            "jakarta.validation.constraints.Size",
            "javax.validation.constraints.Size"
    );

    private static final Set<String> PATTERN_ANNOTATIONS = Set.of(
            "jakarta.validation.constraints.Pattern",
            "javax.validation.constraints.Pattern"
    );

    private static final Set<String> MIN_ANNOTATIONS = Set.of(
            "jakarta.validation.constraints.Min",
            "javax.validation.constraints.Min",
            "jakarta.validation.constraints.DecimalMin",
            "javax.validation.constraints.DecimalMin"
    );

    private static final Set<String> MAX_ANNOTATIONS = Set.of(
            "jakarta.validation.constraints.Max",
            "javax.validation.constraints.Max",
            "jakarta.validation.constraints.DecimalMax",
            "javax.validation.constraints.DecimalMax"
    );

    private SchemaGenerator() {
    }

    public static JSONObject generateRequestSchema(PsiMethod method) {
        JSONObject schema = new JSONObject();
        schema.put("$schema", JSON_SCHEMA_DRAFT_7);
        schema.put("title", method.getName());
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("x-javaMethod", buildJavaMethodId(method));

        JSONObject properties = new JSONObject();
        schema.put("properties", properties);

        List<String> required = new ArrayList<>();

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            ParameterSchema parameterSchema = buildParameterSchema(parameter, new HashSet<>());
            properties.put(parameter.getName(), parameterSchema.schema());
            if (parameterSchema.required()) {
                required.add(parameter.getName());
            }
        }

        if (!required.isEmpty()) {
            schema.put("required", new JSONArray(required));
        }

        return schema;
    }

    private static ParameterSchema buildParameterSchema(PsiParameter parameter, Set<String> visited) {
        JSONObject schema = buildSchemaForType(parameter.getType(), parameter.getProject(), visited);
        schema.put("title", parameter.getName());
        schema.put("x-javaType", safeCanonicalText(parameter.getType()));

        ParameterSourceInfo sourceInfo = detectParameterSource(parameter);
        if (sourceInfo != null) {
            schema.put("x-source", sourceInfo.source());
            if (sourceInfo.name() != null) {
                if ("header".equals(sourceInfo.source())) {
                    schema.put("x-headerName", sourceInfo.name());
                } else if ("query".equals(sourceInfo.source())) {
                    schema.put("x-paramName", sourceInfo.name());
                } else if ("path".equals(sourceInfo.source())) {
                    schema.put("x-pathVariable", sourceInfo.name());
                } else if ("form".equals(sourceInfo.source())) {
                    schema.put("x-formField", sourceInfo.name());
                }
            }
        }

        String defaultValue = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestParam", "defaultValue");
        if (defaultValue != null && !defaultValue.isBlank()) {
            schema.put("default", defaultValue);
        }

        Constraints constraints = extractConstraints(parameter);
        applyConstraints(schema, constraints);

        boolean required = determineRequired(parameter, sourceInfo, constraints);
        if (required) {
            schema.put("x-required", true);
        }

        return new ParameterSchema(schema, required);
    }

    private static JSONObject buildSchemaForType(PsiType type, Project project, Set<String> visited) {
        if (type instanceof PsiArrayType arrayType) {
            JSONObject schema = new JSONObject();
            schema.put("type", "array");
            schema.put("items", buildSchemaForType(arrayType.getComponentType(), project, visited));
            schema.put("x-javaType", safeCanonicalText(type));
            return schema;
        }

        String canonical = safeCanonicalText(type);
        if (isMultipartFile(canonical)) {
            JSONObject schema = new JSONObject();
            schema.put("type", "string");
            schema.put("format", "binary");
            schema.put("x-javaType", canonical);
            return schema;
        }

        if (isPrimitiveOrWrapper(canonical)) {
            return primitiveSchema(canonical);
        }

        if (type instanceof PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass != null) {
                if (psiClass.isEnum()) {
                    return buildEnumSchema(psiClass, canonical);
                }
                if (isCollectionClass(psiClass, project)) {
                    PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                    JSONObject schema = new JSONObject();
                    schema.put("type", "array");
                    schema.put("x-javaType", canonical);
                    schema.put("items", iterableType != null
                            ? buildSchemaForType(iterableType, project, visited)
                            : new JSONObject().put("type", "object"));
                    return schema;
                }
                if (isMapClass(psiClass, project)) {
                    PsiType keyType = PsiUtil.substituteTypeParameter(type, "java.util.Map", 0, false);
                    PsiType valueType = PsiUtil.substituteTypeParameter(type, "java.util.Map", 1, false);
                    JSONObject schema = new JSONObject();
                    schema.put("type", "object");
                    schema.put("x-javaType", canonical);
                    if (valueType != null) {
                        schema.put("additionalProperties", buildSchemaForType(valueType, project, visited));
                    } else {
                        schema.put("additionalProperties", true);
                    }
                    if (keyType != null) {
                        schema.put("x-keyType", safeCanonicalText(keyType));
                    }
                    return schema;
                }
                return buildPojoSchema(psiClass, project, visited);
            }
        }

        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        schema.put("x-javaType", canonical);
        return schema;
    }

    private static JSONObject buildPojoSchema(PsiClass psiClass, Project project, Set<String> visited) {
        String qName = psiClass.getQualifiedName();
        if (qName != null && !visited.add(qName)) {
            JSONObject schema = new JSONObject();
            schema.put("type", "object");
            schema.put("x-javaType", qName);
            return schema;
        }

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("x-javaType", qName != null ? qName : psiClass.getName());
        schema.put("additionalProperties", false);

        JSONObject properties = new JSONObject();
        List<String> required = new ArrayList<>();

        for (PsiField field : psiClass.getAllFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (!Objects.equals(field.getContainingClass(), psiClass)) {
                continue;
            }
            String fieldName = field.getName();
            if (fieldName == null) {
                continue;
            }
            JSONObject fieldSchema = buildSchemaForType(field.getType(), project, visited);
            fieldSchema.put("x-javaType", safeCanonicalText(field.getType()));

            Constraints constraints = extractConstraints(field);
            applyConstraints(fieldSchema, constraints);

            boolean requiredFlag = constraints.required();
            if (requiredFlag) {
                required.add(fieldName);
                fieldSchema.put("x-required", true);
            }

            properties.put(fieldName, fieldSchema);
        }

        if (!properties.isEmpty()) {
            schema.put("properties", properties);
        }
        if (!required.isEmpty()) {
            schema.put("required", new JSONArray(required));
        }

        if (qName != null) {
            visited.remove(qName);
        }

        return schema;
    }

    private static JSONObject buildEnumSchema(PsiClass enumClass, String canonical) {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        schema.put("x-javaType", canonical);
        JSONArray enums = new JSONArray();
        for (PsiField field : enumClass.getFields()) {
            if (field instanceof PsiEnumConstant constant) {
                enums.put(constant.getName());
            }
        }
        schema.put("enum", enums);
        return schema;
    }

    private static boolean isMultipartFile(String canonical) {
        if (canonical == null) {
            return false;
        }
        return canonical.equals("org.springframework.web.multipart.MultipartFile")
                || canonical.equals("jakarta.servlet.http.Part")
                || canonical.equals("javax.servlet.http.Part");
    }

    private static boolean isPrimitiveOrWrapper(String canonical) {
        if (canonical == null) return false;
        return canonical.equals("byte") || canonical.equals("java.lang.Byte")
                || canonical.equals("short") || canonical.equals("java.lang.Short")
                || canonical.equals("int") || canonical.equals("java.lang.Integer")
                || canonical.equals("long") || canonical.equals("java.lang.Long")
                || canonical.equals("float") || canonical.equals("java.lang.Float")
                || canonical.equals("double") || canonical.equals("java.lang.Double")
                || canonical.equals("boolean") || canonical.equals("java.lang.Boolean")
                || canonical.equals("char") || canonical.equals("java.lang.Character")
                || canonical.equals("java.lang.String")
                || canonical.equals("java.math.BigDecimal")
                || canonical.equals("java.math.BigInteger")
                || canonical.equals("java.time.LocalDate")
                || canonical.equals("java.time.LocalDateTime")
                || canonical.equals("java.time.OffsetDateTime")
                || canonical.equals("java.time.Instant")
                || canonical.equals("java.util.Date");
    }

    private static JSONObject primitiveSchema(String canonical) {
        JSONObject schema = new JSONObject();
        schema.put("x-javaType", canonical);
        switch (canonical) {
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "long":
            case "java.lang.Long":
            case "java.math.BigInteger":
                schema.put("type", "integer");
                break;
            case "float":
            case "java.lang.Float":
            case "double":
            case "java.lang.Double":
            case "java.math.BigDecimal":
                schema.put("type", "number");
                break;
            case "boolean":
            case "java.lang.Boolean":
                schema.put("type", "boolean");
                break;
            case "java.time.LocalDate":
                schema.put("type", "string");
                schema.put("format", "date");
                break;
            case "java.time.LocalDateTime":
            case "java.time.OffsetDateTime":
            case "java.time.Instant":
                schema.put("type", "string");
                schema.put("format", "date-time");
                break;
            case "java.util.Date":
                schema.put("type", "string");
                schema.put("format", "date-time");
                break;
            case "char":
            case "java.lang.Character":
                schema.put("type", "string");
                schema.put("minLength", 1);
                schema.put("maxLength", 1);
                break;
            default:
                schema.put("type", "string");
        }
        return schema;
    }

    private static Constraints extractConstraints(PsiModifierListOwner owner) {
        boolean required = false;
        Integer minLength = null;
        Integer maxLength = null;
        Integer minItems = null;
        Integer maxItems = null;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        String pattern = null;

        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return new Constraints(required, minLength, maxLength, minItems, maxItems, minimum, maximum, pattern);
        }

        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) continue;

            if (NOT_NULL_ANNOTATIONS.contains(fqn)) {
                required = true;
                if (fqn.endsWith("NotBlank") || fqn.endsWith("NotEmpty")) {
                    minLength = minLength == null ? 1 : Math.max(minLength, 1);
                }
            } else if (SIZE_ANNOTATIONS.contains(fqn)) {
                Integer min = parseIntegerAttribute(annotation, "min");
                Integer max = parseIntegerAttribute(annotation, "max");
                if (min != null) {
                    minLength = mergeMin(minLength, min);
                    minItems = mergeMin(minItems, min);
                }
                if (max != null && max >= 0) {
                    maxLength = mergeMax(maxLength, max);
                    maxItems = mergeMax(maxItems, max);
                }
            } else if (PATTERN_ANNOTATIONS.contains(fqn)) {
                String regexp = extractAttribute(annotation, "regexp");
                if (regexp == null || regexp.isBlank()) {
                    regexp = extractAttribute(annotation, "value");
                }
                if (regexp != null && !regexp.isBlank()) {
                    pattern = regexp;
                }
            } else if (MIN_ANNOTATIONS.contains(fqn)) {
                BigDecimal value = parseDecimalAttribute(annotation, "value");
                if (value == null) {
                    value = parseDecimalAttribute(annotation, "min");
                }
                if (value != null) {
                    minimum = mergeMinDecimal(minimum, value);
                }
            } else if (MAX_ANNOTATIONS.contains(fqn)) {
                BigDecimal value = parseDecimalAttribute(annotation, "value");
                if (value == null) {
                    value = parseDecimalAttribute(annotation, "max");
                }
                if (value != null) {
                    maximum = mergeMaxDecimal(maximum, value);
                }
            }
        }

        return new Constraints(required, minLength, maxLength, minItems, maxItems, minimum, maximum, pattern);
    }

    private static void applyConstraints(JSONObject schema, Constraints constraints) {
        if (constraints.pattern() != null && isStringSchema(schema)) {
            schema.put("pattern", constraints.pattern());
        }
        if (constraints.minLength() != null && isStringSchema(schema)) {
            schema.put("minLength", constraints.minLength());
        }
        if (constraints.maxLength() != null && isStringSchema(schema)) {
            schema.put("maxLength", constraints.maxLength());
        }
        if (constraints.minItems() != null && isArraySchema(schema)) {
            schema.put("minItems", constraints.minItems());
        }
        if (constraints.maxItems() != null && isArraySchema(schema)) {
            schema.put("maxItems", constraints.maxItems());
        }
        if (constraints.minimum() != null && isNumberSchema(schema)) {
            schema.put("minimum", constraints.minimum());
        }
        if (constraints.maximum() != null && isNumberSchema(schema)) {
            schema.put("maximum", constraints.maximum());
        }
    }

    private static boolean determineRequired(PsiParameter parameter, ParameterSourceInfo sourceInfo, Constraints constraints) {
        if (constraints.required()) {
            return true;
        }
        if (sourceInfo == null) {
            return false;
        }
        String source = sourceInfo.source();
        if ("path".equals(source)) {
            return true;
        }
        if ("body".equals(source)) {
            String requiredAttr = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestBody", "required");
            if (requiredAttr == null) {
                return true;
            }
            return Boolean.parseBoolean(requiredAttr);
        }
        if ("query".equals(source)) {
            String requiredAttr = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestParam", "required");
            if (requiredAttr != null) {
                return Boolean.parseBoolean(requiredAttr);
            }
            String defaultValue = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestParam", "defaultValue");
            return defaultValue == null || defaultValue.isBlank();
        }
        if ("header".equals(source)) {
            String requiredAttr = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestHeader", "required");
            if (requiredAttr != null) {
                return Boolean.parseBoolean(requiredAttr);
            }
            String defaultValue = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestHeader", "defaultValue");
            return defaultValue == null || defaultValue.isBlank();
        }
        if ("form".equals(source)) {
            return true;
        }
        return false;
    }

    private static boolean isStringSchema(JSONObject schema) {
        return "string".equals(schema.optString("type"));
    }

    private static boolean isArraySchema(JSONObject schema) {
        return "array".equals(schema.optString("type"));
    }

    private static boolean isNumberSchema(JSONObject schema) {
        String type = schema.optString("type");
        return "number".equals(type) || "integer".equals(type);
    }

    private static ParameterSourceInfo detectParameterSource(PsiParameter parameter) {
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.PathVariable")) {
            String name = extractAttr(parameter, "org.springframework.web.bind.annotation.PathVariable", "value");
            if (name == null || name.isBlank()) {
                name = parameter.getName();
            }
            return new ParameterSourceInfo("path", name);
        }
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestParam")) {
            String name = extractRequestParamName(parameter);
            return new ParameterSourceInfo("query", name != null ? name : parameter.getName());
        }
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestHeader")) {
            String name = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestHeader", "value");
            if (name == null || name.isBlank()) {
                name = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestHeader", "name");
            }
            if (name == null || name.isBlank()) {
                name = parameter.getName();
            }
            return new ParameterSourceInfo("header", name);
        }
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestPart")) {
            String name = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestPart", "value");
            if (name == null || name.isBlank()) {
                name = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestPart", "name");
            }
            return new ParameterSourceInfo("form", name != null ? name : parameter.getName());
        }
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestBody")) {
            return new ParameterSourceInfo("body", null);
        }

        PsiType type = parameter.getType();
        String canonical = safeCanonicalText(type);
        if (isMultipartFile(canonical)) {
            return new ParameterSourceInfo("form", parameter.getName());
        }
        if (type != null) {
            if (isSimpleType(canonical)) {
                return new ParameterSourceInfo("query", parameter.getName());
            } else {
                return new ParameterSourceInfo("body", null);
            }
        }
        return null;
    }

    private static boolean isSimpleType(String canonical) {
        return isPrimitiveOrWrapper(canonical);
    }

    private static boolean hasAnnotation(PsiModifierListOwner owner, String annotationFqn) {
        return owner.getAnnotation(annotationFqn) != null;
    }

    private static String extractAttr(PsiModifierListOwner owner, String annotationFqn, String attr) {
        PsiAnnotation annotation = owner.getAnnotation(annotationFqn);
        if (annotation == null) {
            return null;
        }
        PsiAnnotationMemberValue value = attr != null ? annotation.findAttributeValue(attr) : annotation.findAttributeValue(null);
        if (value == null) {
            return null;
        }
        return extractString(value);
    }

    private static String extractRequestParamName(PsiParameter parameter) {
        String value = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestParam", "value");
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = extractAttr(parameter, "org.springframework.web.bind.annotation.RequestParam", "name");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return null;
    }

    private static Integer parseIntegerAttribute(PsiAnnotation annotation, String attr) {
        String value = extractAttribute(annotation, attr);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal parseDecimalAttribute(PsiAnnotation annotation, String attr) {
        String value = extractAttribute(annotation, attr);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replaceAll("\"", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String extractAttribute(PsiAnnotation annotation, String attribute) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
        if (value == null && attribute == null) {
            value = annotation.findAttributeValue(null);
        }
        if (value == null) {
            return null;
        }
        return extractString(value);
    }

    private static String extractString(PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression literal) {
            Object raw = literal.getValue();
            return raw instanceof String ? (String) raw : Objects.toString(raw, "");
        }
        String text = value.getText();
        if (text == null) {
            return "";
        }
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static Integer mergeMin(Integer current, Integer candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        return Math.max(current, candidate);
    }

    private static Integer mergeMax(Integer current, Integer candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        return Math.min(current, candidate);
    }

    private static BigDecimal mergeMinDecimal(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        return current.max(candidate);
    }

    private static BigDecimal mergeMaxDecimal(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        return current.min(candidate);
    }

    private static String safeCanonicalText(PsiType type) {
        if (type == null) return "";
        String text = type.getCanonicalText();
        return text != null ? text : type.getPresentableText();
    }

    private static String buildJavaMethodId(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ? containingClass.getQualifiedName() : null;
        if (className == null) {
            className = containingClass != null ? containingClass.getName() : "";
        }
        return className + "#" + method.getName();
    }

    private static boolean isCollectionClass(PsiClass psiClass, Project project) {
        if (psiClass == null) {
            return false;
        }
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass collectionClass = facade.findClass("java.util.Collection", GlobalSearchScope.allScope(project));
        return collectionClass != null && psiClass.isInheritor(collectionClass, true);
    }

    private static boolean isMapClass(PsiClass psiClass, Project project) {
        if (psiClass == null) {
            return false;
        }
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass mapClass = facade.findClass("java.util.Map", GlobalSearchScope.allScope(project));
        return mapClass != null && psiClass.isInheritor(mapClass, true);
    }

    private record ParameterSchema(JSONObject schema, boolean required) {}

    private record ParameterSourceInfo(String source, String name) {}

    private record Constraints(boolean required,
                               Integer minLength,
                               Integer maxLength,
                               Integer minItems,
                               Integer maxItems,
                               BigDecimal minimum,
                               BigDecimal maximum,
                               String pattern) {}
}

