package com.pela.platform.adapter.out.contentfilter;

import com.pela.shared.application.port.out.ContentFilterPort;
import com.pela.shared.domain.text.WordBlocklist;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * BR-15: lớp lọc từ cấm tiếng Anh + tiếng Việt, đọc từ file (mỗi dòng một từ/cụm từ, '#' là chú thích).
 * So khớp theo ranh giới từ ({@link WordBlocklist}). Không log văn bản được kiểm.
 */
@Component
public class BlocklistContentFilterAdapter implements ContentFilterPort {

    private final WordBlocklist blocklist;

    public BlocklistContentFilterAdapter(ResourceLoader resources,
            @Value("${pela.content-filter.files:classpath:content-filter/blocklist-en.txt,classpath:content-filter/blocklist-vi.txt}")
            List<String> files) {
        this.blocklist = WordBlocklist.of(load(resources, files));
    }

    @Override
    public ContentCheck check(String text) {
        List<String> matches = blocklist.findMatches(text);
        return new ContentCheck(matches.isEmpty(), matches);
    }

    private static List<String> load(ResourceLoader resources, List<String> files) {
        List<String> entries = new ArrayList<>();
        for (String file : files) {
            Resource resource = resources.getResource(file.trim());
            try (InputStream in = resource.getInputStream()) {
                entries.addAll(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read content filter list " + file, e);
            }
        }
        if (entries.stream().noneMatch(line -> !line.isBlank() && !line.trim().startsWith("#"))) {
            throw new IllegalStateException("Content filter lists are empty");
        }
        return entries;
    }
}
