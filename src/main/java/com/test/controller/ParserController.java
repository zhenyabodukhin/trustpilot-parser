package com.test.controller;

import com.test.dto.ResultDTO;
import com.test.service.ParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class ParserController {

    private final ParserService parserService;

    @GetMapping("/reviews/{domain}")
    public Mono<ResultDTO> getParsingResponse(@PathVariable String domain) {
        return parserService.parse(domain);
    }
}
