# Ktor2Curl: A Ktor Plugin for Generating cURL Commands

Simple way to transform Ktor requests into cURL logs. Pure Kotllin library, supports both KMP and Android projects.
It is inspired by [Ok2Curl](https://github.com/mrmike/Ok2Curl), which does the same for OkHttp.

# Install

### Kotlin Multiplatform Projects

Add the following dependency to your commonMain source set:

```kotlin
val commonMain by getting {
    dependencies {
        implementation("io.github.kabirnayeem99:ktor2curl:1.0.2")
    }
}
```

### Android Projects

Use the following dependency in your app module's build.gradle file:

#### Kotlin DSL (`build.gradle.kts`)

```kotlin
dependencies {
  // all other dependencies
  implementation("io.github.kabirnayeem99:ktor2curl:1.0.2")
}
```

#### Groovy DSL (`build.gradle`)

```groovy
dependencies {
    // all other dependencies
    implementation 'io.github.kabirnayeem99:ktor2curl:1.0.2'
}
```

## Usage

To install the plugin in your Ktor client:

```kotlin
val client = HttpClient(CIO) {
    install(KtorToCurl) {
        converter = object : CurlLogger {
            override fun log(curl: String) {
                println(curl)
            }
        }
    }
}
client.post("https://api.greenbirdregistry.com/v1/child-green-bird/bird-count") {
    headers {
        append(HttpHeaders.Authorization, "Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg")
        append(HttpHeaders.UserAgent, "KtorClient/3.0.2")
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
    setBody("""{"date": "2024-10-09", "bird_count": 16400}""")
}
```
Output: 
```shell
curl -X POST \
  https://api.greenbirdregistry.com/v1/child-green-bird/bird-count \
  -H "Authorization: Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg" \
  -H "User-Agent: KtorClient/3.0.2" \
  -H "Content-Type: application/json" \
  --data '{"date": "2024-10-09", "bird_count": 16400}'
```
For further configurations, such as excluding specific headers or masking sensitive information:

```kotlin
val client = HttpClient(CIO) {
    install(KtorToCurl) {
        converter = object : CurlLogger {
            override fun log(curl: String) {
                println(curl)
            }
        }
        excludedHeaders = setOf("User-Agent")  // Headers to exclude from logging
        maskedHeaders = setOf("Authorization")  // Headers to mask in the log
    }
}
client.post("https://api.greenbirdregistry.com/v1/child-green-bird/bird-count") {
    headers {
        append(HttpHeaders.Authorization, "Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg")
        append(HttpHeaders.UserAgent, "KtorClient/3.0.2")
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
    setBody("""{"date": "2024-10-09", "bird_count": 16400}""")
}
```
Output:
```shell
curl -X POST \
  https://api.greenbirdregistry.com/v1/child-green-bird/bird-count \
  -H "Authorization: [omitted]" \
  -H "Content-Type: application/json" \
  --data '{"date": "2024-10-09", "bird_count": 16400}'
```

## Contributions

We welcome contributions!
If you have any suggestions or improvements for Ktor2Curl, feel free to open an issue or make a PR.
