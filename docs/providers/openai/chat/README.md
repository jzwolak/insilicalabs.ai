# Developers' Notes: Open AI - Chat Completions

This document provides insight into specific aspects of the OpenAI Chat Completions API and how the `insilicalabs.ai` 
library interfaces with it. It is intended for developers who are contributing to the `insilicalabs.ai` project-- 
whether by building new features, improving documentation, or extending its capabilities.

See [Open AI - Chat Completions API](https://platform.openai.com/docs/api-reference/chat) for the full API  reference.

## OpenAI Chat Completions API

This section describes selected aspects of the OpenAI Chat Completions API as it relates to the internals of the 
`insilicalabs.ai` library.


### What does a request look like?

Requests for non-streaming and streaming cases appear below.


#### Non-streaming Request

A complete HTTP request to the OpenAI Chat Completions API appears below.

```
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

A request, at a minimum, must define:
1. For the HTTP portion:
   1. the HTTP method in the `:method` key, which is always `:post` 
   1. the URL in the `:url` key
   1. the API key and any related information in the `:headers` key
   1. the OpenAI request itself as stringified JSON in the `:body` key
1. For the OpenAI request (which is stringified JSON):
   1. the model in the `model` property
   1. at least one message in the `messages` property

Additional options are available in the OpenAI request such as setting the number of responses to generate with `n`, 
controlling the randomness with `temperature`, setting the maximum number of tokens with `max_tokens`, etc.  See
[Open AI - Chat Completions API](https://platform.openai.com/docs/api-reference/chat) for the full API  reference.

The `insilicalabs.ai` library handles the building of the request.

#### Streaming Request

A streaming request looks the same as the non-streaming request with the addition of `stream: true` as a top-level
key-value pair in the stringified JSON request data.

Note that a streaming request ignores the `n` parameter, which controls how many completions to generate.  A streaming
request will generate one completion per request.

As with the non-streaming request, the `insilicalabs.ai` library handles the building of the streaming request as well.


### What does a response look like?

A complete response, including the HTTP portion and the content (e.g., the HTTP payload), from the OpenAI Chat 
Completions API appears below.  This example depicts a non-streaming response; a streaming response is discussed later.

```
{:cached nil, 
 :request-time 3089, 
 :repeatable? false, 
 :protocol-version {:name HTTP, :major 1, :minor 1}, 
 :streaming? true, 
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

```
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
  ]
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

Each data chunk can contain zero or more tokens, though it's common for the first and last chunks to contain zero tokens 
and other chunks to contain one token.  A token could be several words and/or punctuation marks, but it's common for a 
token to consist only of a word, part of a word, or a single punctuation mark.

The `finish_reason` gives the reason that the model stopped generating.  See
[Finish Reason in Response](#finish-reason-in-response) for an explanation of the `finish_reason`.

#### Finish Reason in Response

The reason the model stopped generating is given in the response `finish_reason` property.  The field appears only in a
successful HTTP response, e.g. HTTP code `200`, and applies to both non-streaming and streaming modes.  The table below
describes the finish reasons.

| Finish Reason  | Error? | Done Generating? | Meaning                                   |
|----------------|--------|------------------|-------------------------------------------|
| stop           | no     | yes              | Normal completion                         |
| length         | yes    | yes              | Hit max token limit                       |
| content_filter | yes    | yes              | Blocked by safety filters                 |
| tool_calls     | no     | no               | Model paused to make a tool/function call |
| function_call  | no     | no               | Legacy tool calling                       |
| null           | no     | no               | Used during streaming in-between chunks   |

A finish reason of `length` or `content_filter` indicates an error with the request.  Note that these conditions can
indicate an error while the HTTP response code of `200` indicates success since the HTTP response code applies to the
HTTP request itself.

### How to know if a response was successful or not?

The `insilicalabs.ai` library indicates a successful response only if:
1. The HTTP response code is `200`.  See [HTTP Response Codes](#http-response-codes).
1. No errors were encountered when reading or parsing the response. 
1. The `finish_reason` does not indicate an error, e.g. the `finish_reason` is not `length` or `content_filter`.  See
   [Finish Reason in Response](#finish-reason-in-response).


### How to know if a streaming response is done generating?

The request was accepted and model should start generating if the HTTP response code was `200`.

The streaming request is done generating SUCCESSFULLY if a finish reason of `stop` is received (see 
[Finish Reason in Response](#finish-reason-in-response)).  The terminal event `data: [DONE]` will also follow to
indicate the end of the stream.

The stream request is done generating UNSUCCESSFULLY if the finish reason is an error (see
[Finish Reason in Response](#finish-reason-in-response)) or if an exception occurs.  The `insilicalabs.ai` catches
exceptions and returns a map with `:success` set to `false` if an exception or any other failure condition occurs.

### How should a response be parsed?

Parsing [non-streaming](#how-should-a-non-streaming-response-be-parsed) vs. 
[streaming](#how-should-a-streaming-response-be-parsed) responses is described below.

#### How should a non-streaming response be parsed?

[What does a response look like?](#what-does-a-response-look-like) provides an example non-streaming response.

Parse a non-streaming response by:
1. If the HTTP response code was `200`, then the request was accepted and the model should start generating.  Even if a
generating failure occurred, then the HTTP response should contain a payload to process.
1. Not necessary for parsing, but the `insilicalabs.ai` library converts the HTTP headers from string keys to 
kebab-formatted keywords.
1. Retreive the payload of the HTTP response at the key sequence location of `["choices" 0 "message" "content"]`.  If 
the response contains multiple content choices, and it is desired to handle those, then select the choice(s) by 
substituting the desired 0-based choice option as the 2nd argument in the key sequence.  In either case, the selected 
choice represents the entirety of the response.
1. Parse the string to JSON, such as using the [Chesire library](https://github.com/dakrone/cheshire) with 
`(json/parse-string <payload> keyword)`.  The example parse statement converts JSON property names to keywords.
1. Inspect the finish reason to check for success or failure.  See 
[Finish Reason in Response](#finish-reason-in-response).


#### How should a streaming response be parsed?

See [What does a response look like?](#what-does-a-response-look-like) provides an example non-streaming response; the 
HTTP portion (e.g., everything except for the payload in the `body` key applies here).  Then see 
[Streaming Response](#streaming-response) for an example of streaming response payload.

Parse a streaming response by:
1. If the HTTP response code was `200`, then the request was accepted and the model should start generating.  Even if a
   generating failure occurred, then the subsequent HTTP response(s) should contain payloads to process.
1. Not necessary for parsing, but the `insilicalabs.ai` library converts the HTTP headers from string keys to
   kebab-formatted keywords.
1. Accumulate lines until a blank line is encountered.
   1. The content of the response is contained in the `content` property at the key sequence
      `["choices" 0 "delta" "content"]`.  Unlike a non-streaming response, a streaming response cannot have more than one
      choice; setting `n > 1` in a streaming request is ignored.
   1. If an exception occurs while reading, then stop.  Note that the 
      `insilicalabs.ai` library catches the exceptions and returns a map with `:success` equal to `false` in this case.
1. Strip the `:data` prefix.
1. Concatenate the lines to form the full message.
1. Parse the full message as JSON, such as using the [Chesire library](https://github.com/dakrone/cheshire) with
   `(json/parse-string <payload> keyword)`.  The example parse statement converts JSON property names to keywords.
    1. If an exception occurs while parsing, then stop.  Note that the
       `insilicalabs.ai` library catches the exceptions and returns a map with `:success` equal to `false` in this case.
1. Inspect the finish reason to check for success/failure or completion condition.  See
[Finish Reason in Response](#finish-reason-in-response).  The last response, which may or may not have content, will 
have a finish reason of `stop`.  The terminal event `data: [DONE]` will also follow to indicate the end of the stream.
   1. If successful 
      1. and contains content, then return that content
      1. and does not contain content
         1. and is not a stop condition, then continue reading
         1. and is a stop condition, then stop reading
   1. If not successful, the stop reading and return a failure 
