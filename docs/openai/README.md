# Developers' Notes: Open AI

# todo Points to Address
- example request/response
   - non-stream vs stream 
   - response: 
      - headers are json
      - body is string 
- How to know when the model stopped generating?  HTTP response code + finish_reason in response
- Streaming
   - How to parse
   - Types of data it contains, and what it does not contain (e.g. does not support all types from SSE spec)


# HTTP Response Codes

HTTP codes indicate the success or failure with the request itself.  The following table describes common response 
codes:

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


# Finish Reason in Response

The reason the model stopped generating is given in the response `finish_reason` property.  The field appears only in a
successful response, e.g. HTTP code 200 OK, and applies to both streaming and non-streaming modes.  The table below 
describes the finish reasons.

| Finish Reason  | Error? | Done Generating? | Meaning                                                         |
|----------------|--------|------------------|-----------------------------------------------------------------|
| stop           | no     | yes              | Normal completion                                               |
| length         | yes    | yes              | Hit max token limit                                             |
| content_filter | yes    | yes              | Blocked by safety filters                                       |
| tool_calls     | no     | no               | Model paused to make a tool/function call                       |
| function call  | no     | no               | Legacy tool calling                                             |
| null           | no     | no               | Model still generating, used during streaming in-between chunks |