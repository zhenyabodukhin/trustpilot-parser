package com.test.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.test.dto.ResultDTO;
import com.test.exception.ErrorResponse;
import com.test.exception.NotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.springframework.util.MimeTypeUtils.TEXT_HTML;

@Service
public class ParserService {

    private static final String URL = "https://www.trustpilot.com/review/";
    private static final String RATING_CLASS = "typography_heading-m__T_L_X typography_appearance-default__AAY17";
    private static final String REVIEWS_COUNT_CLASS = "typography_body-l__KUYFJ typography_appearance-subtle__8_H2l styles_text__W4hWi";
    private static final String REGEX_DIGITS_ONLY = "[^0-9]";
    private static final String NOT_FOUND_MASSAGE = "Domain {%s} is not found";


    private final Cache<String, ResultDTO> dataCache;
    private final WebClient client;

    public ParserService(WebClient.Builder webClientBuilder) {
        this.dataCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofDays(1))
                .maximumSize(15000)
                .build();
        this.client = webClientBuilder.baseUrl(URL)
                .exchangeStrategies(ExchangeStrategies.builder().codecs(this::acceptedCodecs).build())
                .build();
    }

    public Mono<ResultDTO> parse(String domain) {
        return Mono.justOrEmpty(dataCache.getIfPresent(domain))
                .switchIfEmpty(
                        client.get()
                                .uri(domain)
                                .exchangeToMono(clientResponse -> {
                                    if (clientResponse.statusCode().is2xxSuccessful()) {
                                        return clientResponse.bodyToMono(String.class)
                                                .map(this::parseHtml)
                                                .doOnNext(result -> {
                                                    dataCache.put(domain, result);
                                                });
                                    } else {
                                        throw new NotFoundException(new ErrorResponse(HttpStatus.NOT_FOUND, String.format(NOT_FOUND_MASSAGE, domain)));
                                    }
                                })
                );

    }

    private ResultDTO parseHtml(String html) {
        Document document = Jsoup.parse(html);
        String rating = document.getElementsByClass(RATING_CLASS).get(0).text();
        String reviews = document.getElementsByClass(REVIEWS_COUNT_CLASS).get(0).text().replaceAll(REGEX_DIGITS_ONLY, "");

        return new ResultDTO(Integer.parseInt(reviews), Float.parseFloat(rating));
    }

    private void acceptedCodecs(ClientCodecConfigurer clientCodecConfigurer) {
        clientCodecConfigurer.customCodecs().encoder(new Jackson2JsonEncoder(new ObjectMapper(), TEXT_HTML));
        clientCodecConfigurer.customCodecs().decoder(new Jackson2JsonDecoder(new ObjectMapper(), TEXT_HTML));
    }
}
