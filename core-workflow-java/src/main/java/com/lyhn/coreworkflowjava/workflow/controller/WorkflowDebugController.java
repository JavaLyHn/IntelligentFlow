package com.lyhn.coreworkflowjava.workflow.controller;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 调试和测试接口
 */
@Slf4j
@RestController
@RequestMapping({"/workflow/v1"})
public class WorkflowDebugController {

}