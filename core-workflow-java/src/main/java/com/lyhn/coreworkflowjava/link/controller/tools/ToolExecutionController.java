package com.lyhn.coreworkflowjava.link.controller.tools;

import com.lyhn.coreworkflowjava.link.controller.vo.req.HttpToolRunRequest;
import com.lyhn.coreworkflowjava.link.controller.vo.req.ToolDebugRequest;
import com.lyhn.coreworkflowjava.link.controller.vo.res.HttpToolRunResponse;
import com.lyhn.coreworkflowjava.link.controller.vo.res.ToolDebugResponse;
import com.lyhn.coreworkflowjava.link.execution.ToolExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolExecutionController {

    @Autowired
    private ToolExecutionService toolExecutionService;

    @PostMapping("/http_run")
    public HttpToolRunResponse httpRun(@RequestBody HttpToolRunRequest runParams) {
        return toolExecutionService.httpRun(runParams);
    }

    @PostMapping("/tool_debug")
    public ToolDebugResponse toolDebug(@RequestBody ToolDebugRequest toolDebugParams) {
        return toolExecutionService.toolDebug(toolDebugParams);
    }

}