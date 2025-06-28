# Ktor2Curl: A Ktor Plugin for Generating cURL Commands

Simple way to transform Ktor requests into cURL logs. Pure Kotllin library, supports both KMP and
Android projects.
It is inspired by [Ok2Curl](https://github.com/mrmike/Ok2Curl), which does the same for OkHttp.

# Install

### Kotlin Multiplatform Projects

Add the following dependency to your commonMain source set:

```kotlin
val commonMain by getting {
    dependencies {
        implementation("io.github.kabirnayeem99:ktor2curl:1.1.0")
    }
}
```

### Android Projects

Use the following dependency in your app module's build.gradle file:

#### Kotlin DSL (`build.gradle.kts`)

```kotlin
dependencies {
    // all other dependencies
    implementation("io.github.kabirnayeem99:ktor2curl:1.1.0")
}
```

#### Groovy DSL (`build.gradle`)

```groovy
dependencies {
    // all other dependencies
    implementation 'io.github.kabirnayeem99:ktor2curl:1.1.0'
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

client.post("https://data.techforpalestine.org/api/v2/killed-in-gaza.min.json") {
    headers {
        append(HttpHeaders.Authorization, "Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg")
        append(HttpHeaders.UserAgent, "KtorClient/3.0.2")
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
    setBody("""{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}""")
}
```

Output:

```shell
curl -X POST \
  https://data.techforpalestine.org/api/v2/killed-in-gaza.min.json \
  -H "Authorization: Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg" \
  -H "User-Agent: KtorClient/3.2.0" \
  -H "Content-Type: application/json" \
  --data '{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}'

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
client.post("https://data.techforpalestine.org/api/v2/killed-in-gaza.min.json") {
    headers {
        append(HttpHeaders.Authorization, "Basic SXNyYWVsIGtpbGxzIGNoaWxkcmVuLg")
        append(HttpHeaders.UserAgent, "KtorClient/3.0.2")
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
    setBody("""{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}""")
}
```

Output:

```shell
curl -X POST \
  https://data.techforpalestine.org/api/v2/killed-in-gaza.min.json \
  -H "Authorization: [omitted]" \
  -H "Content-Type: application/json" \
  --data '{"en_name":"Mazen Ahmed Mohammed Al-Kahlout","name":"مازن أحمد محمد الكحلوت","age":52,"dob":"1972-02-05","sex":"m","id":"985194547","source":"u"}'
```

For more information regarding this API, please,
visit: https://data.techforpalestine.org/docs/casualties-daily/.
& If you have any humanity left, speak up against the ongoing oppression of
Terror state of Israel against Palestine.

## Contributions

We welcome contributions!
If you have any suggestions or improvements for Ktor2Curl, feel free to open an issue or make a PR.
