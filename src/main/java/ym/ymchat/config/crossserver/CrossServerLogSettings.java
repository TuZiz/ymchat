package ym.ymchat.config.crossserver;

import java.util.List;

public record CrossServerLogSettings(
    boolean enabled,
    String permission,
    String defaultSince,
    int defaultLimit,
    int maxLimit,
    String timestampFormat,
    String headerFormat,
    String lineFormat,
    List<String> hoverFormat,
    String footerFormat
) {

    private static final List<String> DEFAULT_HOVER_FORMAT = List.of(
        "&#B0B0B0鐠佹澘缍嶇紓鏍у娇: &#FFFFFF%id%",
        "&#B0B0B0閺冨爼妫? &#FFFFFF%time_exact%",
        "&#B0B0B0閺堝秴濮熼崳銊︾垼鐠? &#FFFFFF%server_id%",
        "&#B0B0B0妫版垿浜? &#FFFFFF%channel%",
        "&#B0B0B0閻溾晛顔? &#FFFFFF%sender%",
        "&#FFD700閻愮懓鍤径宥呭煑鐎瑰本鏆ｇ拋鏉跨秿"
    );

    public CrossServerLogSettings {
        permission = blankToDefault(permission, "ymchat.logs");
        defaultSince = blankToDefault(defaultSince, "24h");
        defaultLimit = Math.max(1, defaultLimit);
        maxLimit = Math.max(defaultLimit, maxLimit);
        timestampFormat = blankToDefault(timestampFormat, "MM-dd HH:mm:ss");
        headerFormat = blankToDefault(headerFormat, "&#777777[&#FFB833閺冦儱绻?#777777] &#B0B0B0缁涙盯鈧? &#FFFFFF%filters% &#777777(&#FFFFFF%count%&#777777)");
        lineFormat = blankToDefault(lineFormat, "&#777777[&#B0B0B0%time%&#777777] &#B0B0B0[%server%/%channel%] &#FFFFFF%sender%&#777777: &#FFFFFF%message%");
        hoverFormat = normalizeHoverFormat(hoverFormat);
        footerFormat = blankToDefault(footerFormat, "%previous% &#B0B0B0缁?&#FFFFFF%page%/%pages% &#B0B0B0妞?%next%");
    }

    public static CrossServerLogSettings defaults() {
        return new CrossServerLogSettings(
            true,
            "ymchat.logs",
            "24h",
            10,
            30,
            "MM-dd HH:mm:ss",
            "&#777777[&#FFB833閺冦儱绻?#777777] &#B0B0B0缁涙盯鈧? &#FFFFFF%filters% &#777777(&#FFFFFF%count%&#777777)",
            "&#777777[&#B0B0B0%time%&#777777] &#B0B0B0[%server%/%channel%] &#FFFFFF%sender%&#777777: &#FFFFFF%message%",
            DEFAULT_HOVER_FORMAT,
            "%previous% &#B0B0B0缁?&#FFFFFF%page%/%pages% &#B0B0B0妞?%next%"
        );
    }

    private static List<String> normalizeHoverFormat(List<String> input) {
        if (input == null || input.isEmpty()) {
            return DEFAULT_HOVER_FORMAT;
        }
        List<String> lines = input.stream()
            .filter(line -> line != null && !line.isBlank())
            .toList();
        return lines.isEmpty() ? DEFAULT_HOVER_FORMAT : lines;
    }

    private static String blankToDefault(String input, String fallback) {
        return input == null || input.isBlank() ? fallback : input;
    }
}
