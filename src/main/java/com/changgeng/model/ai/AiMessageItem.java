package com.changgeng.model.ai;
import com.changgeng.tool.ContentWrapper;
import lombok.Data;
import java.util.List;

@Data
public class AiMessageItem {
    private String type;
    private ContentWrapper content;
}
