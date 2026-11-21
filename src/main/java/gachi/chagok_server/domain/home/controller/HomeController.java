package gachi.chagok_server.domain.home.controller;

import gachi.chagok_server.domain.home.dto.HomeDtos.Response;
import gachi.chagok_server.domain.home.service.HomeService;
import gachi.chagok_server.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    public ApiResponse<Response> home(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(homeService.home(userId));
    }
}
