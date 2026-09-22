package com.morningstar.old.infra.util;

import com.morningstar.old.demo.pojo.bo.DaasFieldDictItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典请求级缓存持有者：以 ThreadLocal 暂存当前工具调用内已查出的码值列表（daasId → 列表）。
 *
 * <p>{@link #begin()} 同时是"窗口已开启、有人负责清理"的标记：窗口外 {@link #get()} 返回 null，
 * 调用方应直接查库而不缓存，保证切面未覆盖的场景行为安全。</p>
 */
public final class DaasDictCacheHolder {

    private static final ThreadLocal<Map<String, List<DaasFieldDictItem>>> CACHE = new ThreadLocal<>();

    private DaasDictCacheHolder() {
    }

    /**
     * 开启缓存窗口：挂上空 map，标记当前线程有人负责清理
     */
    public static void begin() {
        CACHE.set(new HashMap<>());
    }

    /**
     * 当前线程的缓存（未 {@link #begin()} 时为 null，表示窗口外，不应缓存）
     */
    public static Map<String, List<DaasFieldDictItem>> get() {
        return CACHE.get();
    }

    public static void clear() {
        CACHE.remove();
    }
}