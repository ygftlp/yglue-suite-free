package org.yglue.flow.idea;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ExportMetadataAction extends AnAction {

    private static final Set<String> API_ANNOTATIONS = Set.of(
            "org.yglue.flow.annotations.FlowApi"
    );

    private static final Set<String> OPERATION_ANNOTATIONS = Set.of(
            "org.yglue.flow.annotations.FlowOperation",
            "org.yglue.flow.annotations.DevflowOperation"
    );

    private static final Set<String> REST_CONTROLLER_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RestController"
    );
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "org.springframework.stereotype.Controller"
    );
    private static final Set<String> RESPONSE_BODY_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.ResponseBody"
    );
    private static final Set<String> CLASS_MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping"
    );
    private static final Map<String, String> METHOD_FIXED_MAPPINGS = Map.ofEntries(
            Map.entry("org.springframework.web.bind.annotation.GetMapping", "GET"),
            Map.entry("org.springframework.web.bind.annotation.PostMapping", "POST"),
            Map.entry("org.springframework.web.bind.annotation.PutMapping", "PUT"),
            Map.entry("org.springframework.web.bind.annotation.DeleteMapping", "DELETE"),
            Map.entry("org.springframework.web.bind.annotation.PatchMapping", "PATCH")
    );
    private static final Set<String> METHOD_MAPPING_ANNOTATIONS = new LinkedHashSet<>(METHOD_FIXED_MAPPINGS.keySet());
    static {
        METHOD_MAPPING_ANNOTATIONS.add("org.springframework.web.bind.annotation.RequestMapping");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        try {
            Path outFile = exportProject(project);
            Messages.showInfoMessage(project, "Exported to " + outFile, "yglue");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Export failed: " + ex.getMessage(), "yglue");
        }
    }

    public static Path exportProject(Project project) throws Exception {
        JSONObject json = scanProject(project);
        String base = project.getBasePath();
        if (base == null) throw new IllegalStateException("Project basePath is null");
        Path outDir = Path.of(base, ".ygflow");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("export.json");
        Files.writeString(outFile, json.toString(2));
        return outFile;
    }

    private static JSONObject scanProject(Project project) {
        JSONObject result = new JSONObject();
        JSONArray apis = new JSONArray();
        JSONArray restEndpoints = new JSONArray();
        result.put("project", project.getName());
        result.put("apis", apis);
        result.put("rests", restEndpoints);

        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        Collection<VirtualFile> vfs = FilenameIndex.getAllFilesByExt(project, "java", scope);

        ReadAction.run(() -> {
            for (VirtualFile vf : vfs) {
                PsiFile psi = psiManager.findFile(vf);
                if (!(psi instanceof PsiJavaFile psiJavaFile)) continue;

                for (PsiClass clazz : psiJavaFile.getClasses()) {
                    handleFlowApiClass(clazz, apis);
                    collectRestEndpoints(clazz, restEndpoints);
                }
            }
        });

        return result;
    }

    /**
     * 从 Spring 注解（@Service、@Component）中获取 bean 名称
     * 
     * Spring bean 名称规则：
     * 1. 如果注解指定了 value 属性，使用该值
     * 2. 如果没有指定 value，Spring 默认使用类名首字母小写（如 UserService -> userService）
     * 
     * @param aClass 类对象
     * @return bean 名称，如果类没有 Spring 注解则返回 null
     */
    private static String extractBeanNameFromSpringAnnotation(PsiClass aClass) {
        String beanName = null;
        boolean hasSpringAnnotation = false;
        
        // 检查 @Service 注解
        PsiAnnotation serviceAnno = aClass.getAnnotation("org.springframework.stereotype.Service");
        if (serviceAnno != null) {
            hasSpringAnnotation = true;
            beanName = getAttr(serviceAnno, "value");
        }
        
        // 检查 @Component 注解（如果 @Service 不存在）
        if (!hasSpringAnnotation) {
            PsiAnnotation componentAnno = aClass.getAnnotation("org.springframework.stereotype.Component");
            if (componentAnno != null) {
                hasSpringAnnotation = true;
                beanName = getAttr(componentAnno, "value");
            }
        }
        
        // 如果类有 Spring 注解但未指定 value，使用 Spring 默认规则（类名首字母小写）
        if (hasSpringAnnotation) {
            if (beanName == null || beanName.isBlank()) {
                String className = aClass.getName();
                beanName = generateDefaultBeanName(className);
            }
            return beanName;
        }
        
        return null;
    }
    
    /**
     * 生成默认的 bean 名称（类名首字母小写）
     */
    private static String generateDefaultBeanName(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    private static void handleFlowApiClass(PsiClass aClass, JSONArray apis) {
        PsiAnnotation apiAnno = findAnnotation(aClass, API_ANNOTATIONS);
        if (apiAnno != null) {
            JSONObject apiObj = new JSONObject();
            String qualifiedName = aClass.getQualifiedName();
            apiObj.put("class", qualifiedName != null ? qualifiedName : aClass.getName());

            // 获取 bean 名称（Service name）：优先从 Spring 注解获取，其次从 @FlowApi 的 value 获取，最后从 name 获取
            String beanName = extractBeanNameFromSpringAnnotation(aClass);
            if (beanName == null || beanName.isBlank()) {
                // 从 @FlowApi 的 value 获取
                beanName = getAttr(apiAnno, "value");
                if (beanName.isBlank()) {
                    // 从 @FlowApi 的 name 获取
                    beanName = getAttr(apiAnno, "name");
                    if (beanName.isBlank()) {
                        // 默认使用类名首字母小写
                        String className = aClass.getName();
                        beanName = generateDefaultBeanName(className);
                    }
                }
            }
            
            // API 显示名称：从 @FlowApi 的 name 获取，如果没有则使用类名
            String apiName = emptyToDefault(getAttr(apiAnno, "name"), aClass.getName());
            apiObj.put("name", apiName);
            apiObj.put("beanName", beanName);  // 添加 bean 名称字段
            apiObj.put("description", getAttr(apiAnno, "description"));

            String version = getAttr(apiAnno, "version");
            apiObj.put("version", version.isBlank() ? "1.0.0" : version);

            JSONArray ops = new JSONArray();
            for (PsiMethod m : aClass.getMethods()) {
                PsiAnnotation opAnno = findAnnotation(m, OPERATION_ANNOTATIONS);
                if (opAnno == null) continue;

                JSONObject opObj = new JSONObject();
                opObj.put("method", m.getName());
                opObj.put("name", getAttr(opAnno, "name"));
                opObj.put("description", getAttr(opAnno, "description"));
                opObj.put("tags", toJsonArray(getStringArray(opAnno, "tags")));
                
                // 添加所属 flowApi 的信息，便于前端显示 Service name
                // bean 名称（Service name）：优先从 Spring 注解获取，其次从 @FlowApi 的 value/name 获取
                opObj.put("flowApiBeanName", beanName);  // flowApi 的 bean 名称（Service name）
                opObj.put("flowApiName", apiName);  // flowApi 的显示名称
                String apiClass = qualifiedName != null ? qualifiedName : aClass.getName();
                opObj.put("flowApiClass", apiClass);  // flowApi 的类名

                JSONArray params = new JSONArray();
                for (PsiParameter p : m.getParameterList().getParameters()) {
                    JSONObject pObj = new JSONObject();
                    pObj.put("name", p.getName());
                    pObj.put("type", renderType(p.getType()));
                    params.put(pObj);
                }
                opObj.put("params", params);
                PsiType returnType = m.getReturnType();
                opObj.put("returnType", returnType != null ? renderType(returnType) : "void");
                ops.put(opObj);
            }
            apiObj.put("operations", ops);
            if (!ops.isEmpty()) {
                apis.put(apiObj);
            }
        }
        for (PsiClass inner : aClass.getInnerClasses()) {
            handleFlowApiClass(inner, apis);
        }
    }

    private static void collectRestEndpoints(PsiClass clazz, JSONArray sink) {
        if (!isRestController(clazz)) {
            for (PsiClass inner : clazz.getInnerClasses()) {
                collectRestEndpoints(inner, sink);
            }
            return;
        }
        PsiModifierList modifierList = clazz.getModifierList();
        List<String> classPaths = extractPaths(modifierList, CLASS_MAPPING_ANNOTATIONS);
        if (classPaths.isEmpty()) {
            classPaths = List.of("");
        }

        for (PsiMethod method : clazz.getMethods()) {
            List<Mapping> mappings = extractMethodMappings(method);
            if (mappings.isEmpty()) {
                continue;
            }
            for (Mapping mapping : mappings) {
                List<String> methodPaths = mapping.paths().isEmpty() ? List.of("") : mapping.paths();
                for (String classPath : classPaths) {
                    for (String methodPath : methodPaths) {
                        String fullPath = normalizeUrl(classPath, methodPath);
                        if (fullPath.isBlank()) {
                            fullPath = "/";
                        }
                        JSONObject endpoint = new JSONObject();
                        endpoint.put("class", Objects.toString(clazz.getQualifiedName(), clazz.getName()));
                        endpoint.put("method", method.getName());
                          endpoint.put("path", fullPath);
                          endpoint.put("httpMethod", mapping.httpMethod());
                          endpoint.put("produces", new JSONArray(mapping.produces()));
                          endpoint.put("consumes", new JSONArray(mapping.consumes()));
                          
                          // 提取 JavaDoc 注释
                          String docSummary = extractDocSummary(method);
                          String docDescription = extractDocDescription(method);
                          
                          // name: 优先使用 JavaDoc 的第一行（简洁描述），如果没有则使用方法名
                          if (!docSummary.isBlank()) {
                              endpoint.put("name", docSummary);
                          } else {
                              // 如果没有 JavaDoc，使用方法名作为名称
                              endpoint.put("name", method.getName());
                          }
                          
                          // description: 使用完整的 JavaDoc 描述（包括 @param、@return 等），如果没有则使用 name
                          if (!docDescription.isBlank()) {
                              endpoint.put("description", docDescription);
                          } else if (!docSummary.isBlank()) {
                              endpoint.put("description", docSummary);
                          }
                          
                          endpoint.put("requestSchema", buildRequestSchema(method));
                          endpoint.put("responseSchema", buildResponseSchema(method));
                          sink.put(endpoint);
                      }
                  }
              }
        }

        for (PsiClass inner : clazz.getInnerClasses()) {
            collectRestEndpoints(inner, sink);
        }
    }

    private static boolean isRestController(PsiClass clazz) {
        if (findAnnotation(clazz, REST_CONTROLLER_ANNOTATIONS) != null) {
            return true;
        }
        return findAnnotation(clazz, RESPONSE_BODY_ANNOTATIONS) != null
                && findAnnotation(clazz, CONTROLLER_ANNOTATIONS) != null;
    }

    private static List<Mapping> extractMethodMappings(PsiMethod method) {
        List<Mapping> mappings = new ArrayList<>();
        for (String annoFqn : METHOD_MAPPING_ANNOTATIONS) {
            PsiAnnotation annotation = method.getAnnotation(annoFqn);
            if (annotation == null) {
                continue;
            }
            List<String> paths = extractPaths(annotation);
            List<String> produces = extractStrings(annotation, "produces");
            List<String> consumes = extractStrings(annotation, "consumes");

            if (METHOD_FIXED_MAPPINGS.containsKey(annoFqn)) {
                mappings.add(new Mapping(METHOD_FIXED_MAPPINGS.get(annoFqn), paths, produces, consumes));
            } else {
                List<String> methods = extractRequestMethods(annotation);
                if (methods.isEmpty()) {
                    methods = List.of("GET");
                }
                for (String httpMethod : methods) {
                    mappings.add(new Mapping(httpMethod, paths, produces, consumes));
                }
            }
        }
        return mappings;
    }

    private static List<String> extractPaths(PsiAnnotation annotation) {
        List<String> paths = extractStrings(annotation, "path");
        if (paths.isEmpty()) {
            paths = extractStrings(annotation, "value");
        }
        return paths;
    }

    private static List<String> extractPaths(PsiModifierList modifiers, Set<String> annotations) {
        List<String> result = new ArrayList<>();
        if (modifiers == null) {
            return result;
        }
        for (String annotationFqn : annotations) {
            PsiAnnotation annotation = modifiers.findAnnotation(annotationFqn);
            if (annotation != null) {
                result.addAll(extractPaths(annotation));
            }
        }
        return result;
    }

    private static List<String> extractStrings(PsiAnnotation annotation, String attribute) {
        List<String> values = new ArrayList<>();
        PsiAnnotationMemberValue attr = annotation.findAttributeValue(attribute);
        if (attr == null && Objects.equals(attribute, "value")) {
            attr = annotation.findAttributeValue(null);
        }
        if (attr == null) {
            return values;
        }
        if (attr instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                String value = extractString(initializer);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        } else {
            String value = extractString(attr);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static List<String> extractRequestMethods(PsiAnnotation annotation) {
        List<String> result = new ArrayList<>();
        PsiAnnotationMemberValue attr = annotation.findAttributeValue("method");
        if (attr == null) {
            return result;
        }
        if (attr instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                String methodName = extractEnumName(initializer);
                if (!methodName.isBlank()) {
                    result.add(methodName);
                }
            }
        } else {
            String methodName = extractEnumName(attr);
            if (!methodName.isBlank()) {
                result.add(methodName);
            }
        }
        return result;
    }

    private static String extractEnumName(PsiAnnotationMemberValue value) {
        if (value instanceof PsiReferenceExpression ref) {
            String qualified = ref.getQualifiedName();
            if (qualified != null) {
                int idx = qualified.lastIndexOf('.') + 1;
                return qualified.substring(idx);
            }
            return Objects.toString(ref.getReferenceName(), "");
        }
        String text = value.getText();
        if (text == null) {
            return "";
        }
        int idx = text.lastIndexOf('.') + 1;
        return text.substring(idx).replace("}", "").replace("{", "");
    }

    private static String normalizeUrl(String classPath, String methodPath) {
        String c = classPath == null ? "" : classPath.trim();
        String m = methodPath == null ? "" : methodPath.trim();
        if (c.isEmpty()) {
            c = "/";
        } else if (!c.startsWith("/")) {
            c = "/" + c;
        }
        if (!m.isEmpty() && !m.startsWith("/")) {
            m = "/" + m;
        }
        String combined = (c + m).replaceAll("//+", "/");
        return combined.replaceAll("/+$", "/");
    }

    private record Mapping(String httpMethod, List<String> paths, List<String> produces, List<String> consumes) {}

    private static String getAttr(PsiAnnotation anno, String key) {
        PsiAnnotationMemberValue v = anno.findAttributeValue(key);
        if (v == null) return "";
        return extractString(v);
    }

    private static PsiAnnotation findAnnotation(PsiModifierListOwner owner, Set<String> annotationFqns) {
        for (String fqn : annotationFqns) {
            PsiAnnotation anno = owner.getAnnotation(fqn);
            if (anno != null) {
                return anno;
            }
        }
        return null;
    }

    private static JSONArray toJsonArray(Set<String> values) {
        JSONArray array = new JSONArray();
        for (String v : values) {
            array.put(v);
        }
        return array;
    }

    private static Set<String> getStringArray(PsiAnnotation anno, String key) {
        PsiAnnotationMemberValue value = anno.findAttributeValue(key);
        if (value == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                String extracted = extractString(initializer);
                if (!extracted.isBlank()) {
                    result.add(extracted);
                }
            }
        } else {
            String str = extractString(value);
            if (!str.isBlank()) {
                result.add(str);
            }
        }
        return result;
    }

    private static String extractString(PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression literal) {
            Object raw = literal.getValue();
            return raw instanceof String ? (String) raw : "";
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

    private static String renderType(PsiType type) {
        return type != null ? type.getCanonicalText() : "";
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static JSONArray buildRequestSchema(PsiMethod method) {
        JSONArray schema = new JSONArray();
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            JSONObject item = new JSONObject();
            item.put("name", parameter.getName());
            item.put("type", renderType(parameter.getType()));
            
            // 识别参数来源（@PathVariable, @RequestParam, @RequestBody, @RequestHeader）
            String source = detectParameterSource(parameter);
            if (source != null && !source.isEmpty()) {
                item.put("source", source);
            }
            
            // 对于 @PathVariable，提取路径变量名
            String pathVariableName = extractPathVariableName(parameter);
            if (pathVariableName != null && !pathVariableName.isEmpty()) {
                item.put("pathVariable", pathVariableName);
            }
            
            // 对于 @RequestParam，提取参数名（如果指定）
            String requestParamName = extractRequestParamName(parameter);
            if (requestParamName != null && !requestParamName.isEmpty() && !requestParamName.equals(parameter.getName())) {
                item.put("paramName", requestParamName);
            }
            
            schema.put(item);
        }
        return schema;
    }
    
    /**
     * 检测参数来源：path, query, body, header
     */
    private static String detectParameterSource(PsiParameter parameter) {
        // 检查 @PathVariable
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.PathVariable")) {
            return "path";
        }
        // 检查 @RequestParam
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestParam")) {
            return "query";
        }
        // 检查 @RequestBody
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestBody")) {
            return "body";
        }
        // 检查 @RequestHeader
        if (hasAnnotation(parameter, "org.springframework.web.bind.annotation.RequestHeader")) {
            return "header";
        }
        // 默认：如果没有注解，根据参数位置和类型推断
        // Spring MVC 默认行为：简单类型可能是 @RequestParam，复杂类型可能是 @RequestBody
        PsiType paramType = parameter.getType();
        if (paramType != null) {
            String typeText = paramType.getCanonicalText();
            // 简单类型默认作为 query 参数
            if (isSimpleType(typeText)) {
                return "query";
            }
            // 复杂类型默认作为 body
            return "body";
        }
        return null;
    }
    
    /**
     * 提取 @PathVariable 的变量名
     */
    private static String extractPathVariableName(PsiParameter parameter) {
        PsiAnnotation annotation = parameter.getAnnotation("org.springframework.web.bind.annotation.PathVariable");
        if (annotation == null) {
            return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            value = annotation.findAttributeValue(null); // 尝试默认属性
        }
        if (value != null) {
            String extracted = extractString(value);
            if (!extracted.isBlank()) {
                return extracted;
            }
        }
        // 如果注解中没有指定名称，使用参数名
        return parameter.getName();
    }
    
    /**
     * 提取 @RequestParam 的参数名
     */
    private static String extractRequestParamName(PsiParameter parameter) {
        PsiAnnotation annotation = parameter.getAnnotation("org.springframework.web.bind.annotation.RequestParam");
        if (annotation == null) {
            return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            value = annotation.findAttributeValue("name");
        }
        if (value == null) {
            value = annotation.findAttributeValue(null); // 尝试默认属性
        }
        if (value != null) {
            String extracted = extractString(value);
            if (!extracted.isBlank()) {
                return extracted;
            }
        }
        return null;
    }
    
    /**
     * 检查参数是否有指定注解
     */
    private static boolean hasAnnotation(PsiParameter parameter, String annotationFqn) {
        return parameter.getAnnotation(annotationFqn) != null;
    }
    
    /**
     * 判断是否为简单类型（String, Integer, Long, Double, Boolean 等）
     */
    private static boolean isSimpleType(String typeText) {
        if (typeText == null || typeText.isEmpty()) {
            return false;
        }
        // 基本类型和包装类型
        return typeText.equals("java.lang.String")
                || typeText.equals("java.lang.Integer") || typeText.equals("int")
                || typeText.equals("java.lang.Long") || typeText.equals("long")
                || typeText.equals("java.lang.Double") || typeText.equals("double")
                || typeText.equals("java.lang.Float") || typeText.equals("float")
                || typeText.equals("java.lang.Boolean") || typeText.equals("boolean")
                || typeText.equals("java.lang.Byte") || typeText.equals("byte")
                || typeText.equals("java.lang.Short") || typeText.equals("short")
                || typeText.equals("java.lang.Character") || typeText.equals("char")
                || typeText.equals("java.util.Date")
                || typeText.equals("java.time.LocalDate")
                || typeText.equals("java.time.LocalDateTime")
                || typeText.equals("java.math.BigDecimal")
                || typeText.equals("java.math.BigInteger");
    }

    private static JSONObject buildResponseSchema(PsiMethod method) {
        JSONObject schema = new JSONObject();
        PsiType returnType = method.getReturnType();
        schema.put("type", returnType == null ? "void" : renderType(returnType));
        return schema;
    }

    /**
     * 提取 JavaDoc 的第一行摘要（用于 name 字段）
     * 只提取第一行，作为简洁的名称描述
     */
    private static String extractDocSummary(PsiMethod method) {
        if (!(method instanceof PsiDocCommentOwner)) {
            return "";
        }
        PsiDocCommentOwner owner = (PsiDocCommentOwner) method;
        PsiDocComment doc = owner.getDocComment();
        if (doc == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PsiElement element : doc.getDescriptionElements()) {
            String text = element.getText();
            if (text != null) {
                sb.append(text);
            }
        }
        String fullText = sb.toString().replaceAll("\\s+", " ").trim();
        // 只取第一行作为摘要（遇到换行或句号就截断）
        int firstLineEnd = fullText.length();
        for (int i = 0; i < fullText.length(); i++) {
            char c = fullText.charAt(i);
            if (c == '\n' || c == '\r' || (c == '。' && i > 0)) {
                firstLineEnd = i;
                break;
            }
        }
        String firstLine = fullText.substring(0, firstLineEnd).trim();
        // 如果第一行太长（超过 50 个字符），截取前 50 个字符
        if (firstLine.length() > 50) {
            return firstLine.substring(0, 50).trim();
        }
        return firstLine;
    }
    
    /**
     * 提取完整的 JavaDoc 描述（用于 description 字段）
     * 包括所有描述内容，但不包括 @param、@return 等标签
     */
    private static String extractDocDescription(PsiMethod method) {
        if (!(method instanceof PsiDocCommentOwner)) {
            return "";
        }
        PsiDocCommentOwner owner = (PsiDocCommentOwner) method;
        PsiDocComment doc = owner.getDocComment();
        if (doc == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PsiElement element : doc.getDescriptionElements()) {
            String text = element.getText();
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}
