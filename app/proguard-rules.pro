# 本项目未使用反射 / 序列化，默认规则即可。
# AndroidX 与 Material 都自带 consumer rules，会自动合并进来。

# 保留行号，让线上崩溃堆栈可读（同时隐藏真实源文件名）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
