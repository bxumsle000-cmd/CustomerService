# ============================================
# 使用時機（在專案根目錄執行）：
#
# 平常不會直接用這個 Dockerfile，而是透過 docker-compose.yml：
#     docker compose --profile full up -d --build
# 那樣會連資料庫一起處理好，比較省事（見 docker-compose.yml 的說明）。
#
# 以下是「只想單獨跑 app 容器」時的用法：
#
# 【第一次在這台電腦用 Docker 跑這個專案】（不管原電腦或新電腦都一樣，
#  因為 image 是存在「本機」，只要本機還沒 build 過就要做這步）：
#     docker build -t customer-service .
#     docker run -d -p 8081:8080 --name customer-service customer-service
#   若資料庫不在同一台機器（容器內 localhost 指的是容器自己，連不到主機的
#   資料庫），要用 -e 覆蓋連線字串：
#     docker run -d -p 8081:8080 \
#       -e SPRING_DATASOURCE_URL="jdbc:sqlserver://host.docker.internal:14334;databaseName=customer_service;encrypt=true;trustServerCertificate=true" \
#       --name customer-service customer-service
#
# 【原電腦：改了程式碼，要重新測試】
#   image 是舊的程式碼打包的，必須重新 build 才會生效；同名容器要先刪除
#   才能重開：
#     docker stop customer-service
#     docker rm customer-service
#     docker build -t customer-service .
#     docker run -d -p 8081:8080 --name customer-service customer-service
#
# 【原電腦：程式碼沒改，只是想重開之前建好的容器】
#   不用重新 build，直接把舊容器啟動即可：
#     docker start customer-service
#
# 【原電腦：想確認容器有沒有正常運作 / 除錯】
#     docker logs -f customer-service
#
# 【原電腦：暫時不用了，先關掉但保留容器（之後還能 docker start）】
#     docker stop customer-service
# ============================================

# ---------- 第一階段：使用 Maven + JDK 21 編譯專案 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先複製 pom.xml 並下載依賴，可利用 Docker layer cache 加速後續建置
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 複製原始碼並打包（跳過測試以加快建置速度）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 第二階段：使用輕量 JRE 21 執行 jar ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 從build階段複製打包好的 jar 檔
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
