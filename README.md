# harmonize
## 无单点零配置微服务注册中心

## Dockerfile

```
FROM openjdk:8-jdk-alpine
COPY harmonize.jar harmonize.jar
ENTRYPOINT ["java","-jar","-Xmx1g","/harmonize.jar"]


```

## image

```
docker image build -t fudong/harmonize ./
```


## container run

```
docker container run -it --publish 80:39999 fudong/harmonize
```


## 

```
docker container run -d --publish 80:39999 fudong/harmonize
```

```
 docker ps
 
 docker stop
 
```
