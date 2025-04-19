# Developers' Notes: Open AI - Chat Completions

This document provides insight into specific aspects of the OpenAI Chat Completions API and how the `insilicalabs.ai` 
library interfaces with it. It is intended for developers who are contributing to the `insilicalabs.ai` project-- 
whether by building new features, improving documentation, or extending its capabilities.

See [Open AI - Chat Completions API](https://platform.openai.com/docs/api-reference/chat) for the full API  reference.

## OpenAI Chat Completions API

This section describes selected aspects of the OpenAI Chat Completions API as it relates to the `insilicalabs.ai` 
library.


### What does a request look like?

Requests for non-streaming and streaming cases appear below.


#### Non-streaming Request

A complete HTTP request to the OpenAI Chat Completions API appears below.

```json
{:method :post, 
 :url https://api.openai.com/v1/chat/completions, 
 :content-type :json, 
 :accept :json, 
 :headers {Authorization Bearer <API token>}, 
 :body {
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content":"You are a helpful assistant."
    },
    {
      "role":"user",
      "content": "Write a limmerick about a firefly"
    }
  ]
 }
}
```

A request, at a minimum, must define the `model` property and provide at least one message in the `messages` property.
Additional options are available such as setting the number of responses to generate with `n`, controlling the 
randomness with `temperature`, setting the maximum number of tokens with `max_tokens`, etc.  See
[Open AI - Chat Completions API](https://platform.openai.com/docs/api-reference/chat) for the full API  reference.

Note that the key `:headers` contains the headers, including the API token, as a map using strings as keywords.  The 
key `:body` contains the request data as stringified JSON.


#### Streaming Request

A streaming request looks the same as the non-streaming request with the addition of `"stream": true` as a top-level
key-value pair in the stringified JSON request data.

Note that a streaming request ignores the `n` parameter, which controls how many completions to generate.  A streaming
request will generate one completion per request.


### What does a response look like?

A complete response, including the HTTP portion and the content (e.g., the HTTP payload), from the OpenAI Chat 
Completions API appears below.  This example depicts a non-streaming response; a streaming response is discussed later.

```json
{:cached nil, 
 :request-time 3089, 
 :repeatable? false, 
 :protocol-version {:name HTTP, :major 1, :minor 1}, 
 :streaming? true, 
 :http-client #object[org.apache.http.impl.client.InternalHttpClient 0x67034f09 org.apache.http.impl.client.InternalHttpClient@67034f09], 
 :chunked? true, 
 :cookies {__cf_bm {:discard false, :domain api.openai.com, :expires #inst "2025-04-19T01:45:32.000-00:00", :path /, :secure true, :value <cookie value>, :version 0} }, 
 :reason-phrase OK, 
 :headers {access-control-expose-headers X-Request-ID, openai-organization <organization>, x-ratelimit-reset-requests 120ms, Server <server>, Content-Type application/json, <more cookie information>}, 
 :orig-content-encoding gzip, 
 :status 200, 
 :length -1, 
 :body {
  "id": "chatcmpl-BNr5ouwaQourQ5j742hpcbkfixpnA",
  "object": "chat.completion",
  "created": 1745025332,
  "model": "gpt-4o-2024-08-06",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "There once was a bright firefly,   \nWho danced in the dark evening sky.  \nWith a flicker of light,  \nHe lit up the night,  \nLike a star twinkling up on high.",
        "refusal": null,
        "annotations": []
      },
      "logprobs": null,
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 25,
    "completion_tokens": 34,
    "total_tokens": 59,
    "prompt_tokens_details": {
      "cached_tokens": 0,
      "audio_tokens": 0
    },
    "completion_tokens_details": {
      "reasoning_tokens": 0,
      "audio_tokens": 0,
      "accepted_prediction_tokens": 0,
      "rejected_prediction_tokens": 0
    }
  },
  "service_tier": "default",
  "system_fingerprint": "qf_b8834563c6a"
  }, 
:trace-redirects []}
```

The example above shows a complete, unmodified HTTP response.  The `:body` key contains the response data as a JSON 
string.  In order to interpret the response data, it must be parsed into a JSON object.  The `:headers` key contains 
the headers as a map using strings for keys.

Along with providing the result of processing the response to make it easier to use, the full response is also provided.  
The `insilicalabs.ai` library will modify the response by converting the headers (at `:headers`) and the body 
(at `:body`) to kebab case keywords.

The process of handling a response needs to first consider the [HTTP response code](#http-response-codes).  The payload, or content, of 
the response is similar but differs for a [non-streaming response](#non-streaming-response) vs. a 
[streaming response](#streaming-response).  The ['finish reason' property](#finish-reason-in-response) is important for
understanding the disposition of the response.


#### HTTP Response Codes

HTTP codes indicate the success or failure of the HTTP request itself.  These apply to both non-streaming and streaming
modes.  The following table describes common HTTP response codes:

| HTTP Code | Success/Failure | Meaning               | Common Causes                         |
|-----------|-----------------|-----------------------|---------------------------------------|
| 200       | success         | OK                    | Request successful, data returned     |
| 400       | failure         | Bad request           | Malformed request, invalid parameters |
| 401       | failure         | Unauthorized          | Invalid or missing API key            |
| 403       | failure         | Forbidden             | API key lacks permission              |
| 404       | failure         | Not found             | Invalid endpoint or model             |
| 429       | failure         | Too many requests     | Rate limit exceeded                   |
| 500       | failure         | Internal server error | OpenAI server error                   |
| 502       | failure         | Bad gateway           | Backend issues                        |
| 503       | failure         | Service unavailable   | Server temporarily unavailable        |
| 504       | failure         | Gateway timeout       | Server response timeout               |

An HTTP response code of `200` indicates that the request was successful, and the response contains a payload to be
processed.  

The `insilicalabs.ai` library interprets HTTP codes other than `200` as errors.  The library always returns a map
indicating success or failure of the HTTP request; no exceptions are thrown from the HTTP request mechanism.

#### Non-streaming Response

An example non-streaming response is below. 

```json
{
  "id": "chatcmpl-BNr5ouwaQourQ5j742hpcbkfixpnA",
  "object": "chat.completion",
  "created": 1745025332,
  "model": "gpt-4o-2024-08-06",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "There once was a bright firefly,   \nWho danced in the dark evening sky.  \nWith a flicker of light,  \nHe lit up the night,  \nLike a star twinkling up on high.",
        "refusal": null,
        "annotations": []
      },
      "logprobs": null,
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 25,
    "completion_tokens": 34,
    "total_tokens": 59,
    "prompt_tokens_details": {
      "cached_tokens": 0,
      "audio_tokens": 0
    },
    "completion_tokens_details": {
      "reasoning_tokens": 0,
      "audio_tokens": 0,
      "accepted_prediction_tokens": 0,
      "rejected_prediction_tokens": 0
    }
  }
}
```

The content of the response is contained in the `content` property at the key sequence 
`["choices" 0 "message" "content"]` in this example.  Note that the `choices` property is a list.  If set in the 
request, the model can generate more than one choice; by default, the model generates one choice as shown in this 
example.

The `finish_reason` gives the reason that the model stopped generating.  See 
[Finish Reason in Response](#finish-reason-in-response) for an explanation of the `finish_reason`.


#### Streaming Response

An example streaming response is below.

```json
data: {
  "id": "chatcmpl-abc123",
  "object": "chat.completion.chunk",
  "created": 1677858242,
  "model": "gpt-4",
  "choices": [
    {
      "delta": {
        "role": "assistant"
      },
      "index": 0,
      "finish_reason": null
    }
  ]
}

data: {
  "id": "chatcmpl-abc123",
  "object": "chat.completion.chunk",
  "created": 1677858243,
  "model": "gpt-4",
  "choices": [
    {
      "delta": {
        "content": "Answer"
      },
      "index": 0,
      "finish_reason": null
    }
  ]
}

data: {
  "id": "chatcmpl-abc123",
  "object": "chat.completion.chunk",
  "created": 1677858244,
  "model": "gpt-4",
  "choices": [
    {
      "delta": {
        "content": "here"
      },
      "index": 0,
      "finish_reason": null
    }
  
}

data: {
  "id": "chatcmpl-abc123",
  "object": "chat.completion.chunk",
  "created": 1677858247,
  "model": "gpt-4",
  "choices": [
    {
      "delta": {},
      "index": 0,
      "finish_reason": "stop"
    }
  ]
}

data: [DONE]

```

A streaming response contains a series of partial responses sent using Server-Sent Events (SSE).

OpenAI's SSE streaming implementation supports only two types of data, per the table below.  Note that `event:`, comment 
lines starting with `:`, and fields `retry:`, `id:`, and `name:` are not supported.

| Data Type           | Description                                      |
|---------------------|--------------------------------------------------|
| data: {JSON object} | A standard SSE event link with JSON              |
| data: [DONE]        | A terminal event to signal the end of the stream |

The content of the response is contained in the `content` property at the key sequence 
`["choices" 0 "delta" "content"]`.  Unlike a non-streaming response, a streaming response cannot have more than one 
choice; setting `n > 1` in a streaming request is ignored.  

Each data chunk can contain zero or more tokens, though it's common for the first chunk to contain zero tokens and 
subsequent chunks to contain one token.

The `finish_reason` gives the reason that the model stopped generating.  See
[Finish Reason in Response](#finish-reason-in-response) for an explanation of the `finish_reason`.

#### Finish Reason in Response

The reason the model stopped generating is given in the response `finish_reason` property.  The field appears only in a
successful HTTP response, e.g. HTTP code `200`, and applies to both non-streaming and streaming modes.  The table below
describes the finish reasons.

| Finish Reason  | Error? | Done Generating? | Meaning                                                         |
|----------------|--------|------------------|-----------------------------------------------------------------|
| stop           | no     | yes              | Normal completion                                               |
| length         | yes    | yes              | Hit max token limit                                             |
| content_filter | yes    | yes              | Blocked by safety filters                                       |
| tool_calls     | no     | no               | Model paused to make a tool/function call                       |
| function call  | no     | no               | Legacy tool calling                                             |
| null           | no     | no               | Model still generating, used during streaming in-between chunks |

A finish reason of `length` or `content_filter` indicates an error with the request.  Note that these conditions can
indicate an error while the HTTP response code of `200` indicates success since the HTTP response code applies to the
HTTP request itself.

### How to know if a response was successful or not?

The `insilicalabs.ai` library indicates a successful response only if:
1. The HTTP response code is `200`.  See [HTTP Response Codes](#http-response-codes).
1. No errors were encountered when reading or parsing the response. 
1. The `finish_reason` does not indicate an error, e.g. the `finish_reason` is not `length` or `content_filter`.  See
   [Finish Reason in Response](#finish-reason-in-response).

todo: other cases, like with streaming?


### How to know if a streaming response is done generating?
todo

### How should a response be parsed?
todo

#### How should a non-streaming response be parsed?
todo
- http response code
- headers are string keys.  this library converts to kebab then keywords.
- payload.  json parse, keys to keywords (already kebab?).
- finish_reason
- choices (might have more than 1)


#### How should a streaming response be parsed?
todo
- same as non-streaming: http response code, headers
- data types, difference between SSE standard
- accumulate data
- parse json, keys to keywords.
- finish_reason
- content
   - delta (unlike non-streaming)
   - no choices like in non-streaming.  The n parameter (which controls how many completions to generate) is ignored in 
     streaming mode. Even if you try to set n > 1, only n = 1 is honored in streaming.
- 'DONE'
