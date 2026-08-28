package com.musicdreamer.music.controller;

import com.musicdreamer.common.api.Mess;
import com.musicdreamer.music.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 搜索控制器：/api/v1/search（网关白名单，匿名可搜）。 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 歌曲搜索（标题/歌手/专辑关键词，分页）。 */
    @GetMapping("/songs")
    public Mess songs(@RequestParam("keyword") String keyword,
                      @RequestParam(value = "page", defaultValue = "1") int page,
                      @RequestParam(value = "size", defaultValue = "20") int size) {
        return Mess.ok(searchService.searchSongs(keyword, page, size));
    }

    /** 歌手搜索。 */
    @GetMapping("/singers")
    public Mess singers(@RequestParam("keyword") String keyword,
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return Mess.ok(searchService.searchSingers(keyword, page, size));
    }

    /** 曲风字典（筛选用）。 */
    @GetMapping("/styles")
    public Mess styles() {
        return Mess.ok(searchService.styles());
    }

    /** 按风格/语言浏览。 */
    @GetMapping("/by-style")
    public Mess byStyle(@RequestParam(value = "style", required = false) String style,
                        @RequestParam(value = "language", required = false) String language,
                        @RequestParam(value = "page", defaultValue = "1") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return Mess.ok(searchService.byStyle(style, language, page, size));
    }
}
