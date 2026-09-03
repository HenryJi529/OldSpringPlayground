<script setup lang="ts">
import { RouterView } from 'vue-router';
import zhCN from 'ant-design-vue/locale/zh_CN';
</script>

<template>
    <a-style-provider hashPriority="high">
        <a-config-provider :locale="zhCN">
            <a-app>
                <router-view />
            </a-app>
        </a-config-provider>
    </a-style-provider>
</template>
