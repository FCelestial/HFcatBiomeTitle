# HFcatBiomeTitle

群系标题显示插件，支持数据包群系命名空间识别。

## 构建

```bash
# 需要 JDK 25 + Maven
set JAVA_HOME=F:\env\jdk\azul-25.0.3
mvn clean package
```

## 部署

将 `target/HFcatBiomeTitle-1.0.0.jar` 复制到服务器 `plugins/` 目录，重启服务器。

## 配置

编辑 `config.yml`，在 `biomes:` 段下添加群系条目。

群系键支持三种写法（不区分大小写）：
- `PLAINS` — 枚举名
- `minecraft:plains` — 全命名空间键
- `incendium:ash_barrens` — 数据包群系

## 命令

- `/bt reload` — 重载配置
- `/bt get` — 获取当前位置群系键
- `/bt wand` — 获取区域选区法杖
- `/bt setarea <名称>` — 设置自定义区域
