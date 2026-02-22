package com.spencehouse.logue.service

import okhttp3.Interceptor
import okhttp3.Response

class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        val path = originalRequest.url.encodedPath
        
        // Handle HIDAS (Identity) vs WSC (Honda Services) headers
        if (path.contains("/hidas/")) {
            // Default Accept header for HIDAS
            requestBuilder.header("Accept", "application/json")
            
            if (path.endsWith("register") || path.endsWith("generate")) {
                // These endpoints require form-urlencoded
                requestBuilder.header("Content-Type", "application/x-www-form-urlencoded")
            }
        } else {
            // Most WSC Honda APIs are very strict about headers.
            // Many return XML on error even if JSON is requested, 
            // and they require specific pairs for Success.
            
            // Do NOT set a global default Content-Type or Accept here 
            // if the request already has one from @HeaderMap in the service.
            // OkHttp's @HeaderMap + Interceptor can sometimes create duplicate headers
            // which Honda's servers (ws-dev2.hondaweb.com) reject.
        }

        return chain.proceed(requestBuilder.build())
    }
}
