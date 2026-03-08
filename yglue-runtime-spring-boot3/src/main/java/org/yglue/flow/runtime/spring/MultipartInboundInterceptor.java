package org.yglue.flow.runtime.spring;

import jakarta.servlet.http.Part;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class MultipartInboundInterceptor implements InboundRequestInterceptor {

    @Override
    public String code() {
        return "multipart";
    }

    @Override
    public void apply(InboundRequestContext context, Map<String, Object> config) {
        String contentType = context.request().getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return;
        }

        int maxFileCount = readInt(config, "maxFileCount", 0);
        int maxFileSizeMb = readInt(config, "maxFileSizeMb", 0);
        Set<String> allowedTypes = readStringSet(config, "allowedContentTypes");
        String filesPath = readString(config, "filesPath", "request.files");
        String formPath = readString(config, "formPath", "request.form");

        Map<String, Object> files = new LinkedHashMap<>();
        Map<String, Object> form = new LinkedHashMap<>();
        int fileCount = 0;

        try {
            for (Part part : context.request().getParts()) {
                String fieldName = part.getName();
                String fileName = part.getSubmittedFileName();
                boolean isFile = fileName != null && !fileName.isBlank();

                if (!isFile) {
                    String value = StreamUtils.copyToString(part.getInputStream(), StandardCharsets.UTF_8);
                    mergeValue(form, fieldName, value);
                    continue;
                }

                fileCount += 1;
                if (maxFileCount > 0 && fileCount > maxFileCount) {
                    throw InboundInterceptorException.badRequest("File count exceeds maxFileCount");
                }

                if (maxFileSizeMb > 0) {
                    long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
                    if (part.getSize() > maxBytes) {
                        throw InboundInterceptorException.badRequest("File size exceeds maxFileSizeMb");
                    }
                }

                String fileType = part.getContentType();
                if (!allowedTypes.isEmpty() && (fileType == null || !allowedTypes.contains(fileType))) {
                    throw InboundInterceptorException.badRequest("File content type is not allowed");
                }

                mergeValue(files, fieldName, part);
            }
        } catch (InboundInterceptorException ex) {
            throw ex;
        } catch (Exception ex) {
            throw InboundInterceptorException.badRequest("Failed to parse multipart request");
        }

        context.put(formPath, form);
        context.put(filesPath, files);
    }

    @SuppressWarnings("unchecked")
    private void mergeValue(Map<String, Object> target, String key, Object value) {
        Object existing = target.get(key);
        if (existing == null) {
            target.put(key, value);
            return;
        }
        if (existing instanceof List<?> list) {
            ((List<Object>) list).add(value);
            return;
        }
        List<Object> merged = new ArrayList<>();
        merged.add(existing);
        merged.add(value);
        target.put(key, merged);
    }

    @SuppressWarnings("unchecked")
    private Set<String> readStringSet(Map<String, Object> config, String key) {
        if (config == null) return Set.of();
        Object value = config.get(key);
        if (value == null) return Set.of();
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(text -> !text.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return Set.of();
        String[] arr = text.split(",");
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String item : arr) {
            String normalized = item.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private int readInt(Map<String, Object> config, String key, int fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String readString(Map<String, Object> config, String key, String fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}

