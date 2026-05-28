# CAMPUSON Project

## 1. 프로젝트 개요

CAMPUSON은 대학생들을 위한 통합 커뮤니티 및 정보 공유 플랫폼입니다.  
학생들이 최신 IT 정보, 대학 공지, 취창업 정보, 채팅 기능 등을 한 곳에서 확인할 수 있도록 설계되었습니다.

본 프로젝트는 Spring Boot 기반 웹 애플리케이션으로 개발되었으며, 뉴스 크롤링, 실시간 채팅, 사용자 인증, 게시판 기능 등을 포함합니다.

---

## 2. 주요 기능

- 회원가입 / 로그인
- 개인 및 그룹 채팅
- 최신 IT 뉴스 제공
- 대학 공지 및 취창업 정보 제공
- 메인 페이지 슬라이드 뉴스 UI
- 게시판 기능
- 반응형 웹 UI
- MySQL 기반 데이터 저장
- Spring Security 기반 인증 처리

---

# 3. 개발 환경 (Requirements)

## Backend
- Java JDK 21 이상
- Spring Boot 3.x
- Maven 3.9 이상

## Database
- MySQL 8.x

## Frontend
- Thymeleaf
- HTML5 / CSS3 / JavaScript

## IDE
- IntelliJ IDEA 권장
- VSCode 가능

## OS
- Windows 10/11
- Ubuntu Linux 22.04 이상

---

# 4. 프로젝트 설치 방법

## 4-1. 프로젝트 클론

```bash
git clone https://github.com/programmingG4/adjGroup4.git
4-2. 브랜치 이동
git checkout dev
4-3. MySQL 데이터베이스 생성

MySQL 접속 후 아래 명령 실행:

CREATE DATABASE campuson;
5. application.properties 설정

경로:

src/main/resources/application.properties

예시 설정:

spring.datasource.url=jdbc:mysql://localhost:3306/campuson
spring.datasource.username=root
spring.datasource.password=비밀번호

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
6. 프로젝트 실행 방법
Maven Build

Linux / Ubuntu:

./mvnw clean package

Windows:

mvnw.cmd clean package
Spring Boot 실행

Linux / Ubuntu:

./mvnw spring-boot:run

Windows:

mvnw.cmd spring-boot:run

또는 IntelliJ에서:

CampusonApplication 실행
7. 접속 주소

기본 실행 주소:

http://localhost:8080
8. 프로젝트 구조
src
 ┣ main
 ┃ ┣ java
 ┃ ┃ ┗ com.example.campuson
 ┃ ┣ resources
 ┃ ┃ ┣ static
 ┃ ┃ ┣ templates
 ┃ ┃ ┗ application.properties
 ┗ test
9. 사용 기술 스택
분야	기술
Backend	Spring Boot
Database	MySQL
ORM	Spring Data JPA
Frontend	Thymeleaf
Build Tool	Maven
Version Control	Git / GitHub
10. GitHub Repository

Repository:

https://github.com/programmingG4/adjGroup4

사용 브랜치:

dev
11. 프로젝트 참여자
이름	학번	이메일
김현정	322*****	example1@email.com
박경빈	322*****	example2@email.com
장다혜	322*****	example3@email.com
12. 참고사항
MySQL 서버가 실행 중이어야 합니다.
application.properties 설정값은 로컬 환경에 맞게 수정해야 합니다.
uploads 폴더 및 target 폴더는 Git에 포함하지 않는 것을 권장합니다.
최초 실행 시 Hibernate 설정에 따라 테이블이 자동 생성될 수 있습니다.
13. 라이선스

본 프로젝트는 학습 및 팀 프로젝트 용도로 제작되었습니다.