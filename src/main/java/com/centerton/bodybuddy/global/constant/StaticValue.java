package com.centerton.bodybuddy.global.constant;

public class StaticValue {
    //SUCCESS
    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int ACCEPTED = 202;
    public static final int NO_CONTENT = 204;

    //ERROR
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int RESOURCE_NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int STATE_CONFLICT = 409;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int VALIDATION_ERROR = 422;
    public static final int RATE_LIMITED = 429;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int AI_RESPONSE_INVALID = 502;
    public static final int AI_UNAVAILABLE = 503;
}
