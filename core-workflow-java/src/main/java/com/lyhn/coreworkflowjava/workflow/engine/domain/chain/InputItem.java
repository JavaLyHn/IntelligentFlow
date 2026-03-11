package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputItem {

    // 唯一标识输入项
    @JsonProperty("id")
    private String id;

    // 参数名，在下游引用时会写 ${当前节点名.name}
    /**
     * Input name (e.g., "user_input", "text")
     */
    @JsonProperty("name")
    private String name;

    // 输入参数的类型、取值方式、引用关系等信息
    /**
     * Input schema definition
     */
    @JsonProperty("schema")
    private InputSchema schema;
}
