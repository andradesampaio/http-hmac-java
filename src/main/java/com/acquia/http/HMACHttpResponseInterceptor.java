package com.acquia.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SignatureException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 * An HttpResponseInterceptor that adds X-Server-Authorization-HMAC-SHA256 response header that contains the encrypted response
 * 
 * @author aric.tatan
 *
 */
public class HMACHttpResponseInterceptor implements HttpResponseInterceptor {

    /**
     * The secret key
     */
    protected String secretKey;

    /**
     * The algorithm to use when creating the HMAC
     */
    protected HMACAlgorithm algorithm;

    /**
     * Constructor
     * 
     * @param secretKey; secret key used to encrypt the message
     * @param algorithmName; for example: SHA256
     */
    public HMACHttpResponseInterceptor(String secretKey, String algorithmName) {
        this.secretKey = secretKey;

        HMACAlgorithmFactory algorithmFactory = new HMACAlgorithmFactory();
        this.algorithm = algorithmFactory.createAlgorithm(algorithmName);
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException,
            IOException {
        //get server response signature
        Header serverAuthResponseHeader = response.getFirstHeader(HMACMessageCreator.PARAMETER_X_SERVER_AUTHORIZATION_HMAC_SHA256);
        if (serverAuthResponseHeader == null) {
            throw new HttpException("Error: Server failed to provide response validation.");
        }
        String serverSignature = serverAuthResponseHeader.getValue();

        //get nonce of when request was made
        HMACAuthorizationHeader authHeader = (HMACAuthorizationHeader) context.getAttribute("authHeader");
        if (authHeader == null) {
            throw new HttpException("Error: No authHeader in the HTTP context.");
        }
        String nonce = authHeader.getNonce();

        //get xAuthorizationTimestamp of when request was made
        String xAuthorizationTimestamp = (String) context.getAttribute("xAuthorizationTimestamp");
        if (xAuthorizationTimestamp == null) {
            throw new HttpException("Error: No xAuthorizationTimestamp in the HTTP context.");
        }

        //get server response body
        String responseContent = "";
        HttpEntity entity = response.getEntity();
        if (entity != null && entity.getContentLength() > 0) {
            StringBuilder respStringBuilder = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    entity.getContent()), 1000);
                String line = null;
                while ((line = reader.readLine()) != null) {
                    respStringBuilder.append(line);
                }
            } catch(IOException e) {
                e.printStackTrace();
            } catch(Exception e) {
                e.printStackTrace();
            }
            responseContent = respStringBuilder.toString();
        }

        //check response validity
        HMACMessageCreator messageCreator = new HMACMessageCreator();
        String signableResponseMessage = messageCreator.createSignableResponseMessage(nonce,
            xAuthorizationTimestamp, responseContent);
        String signedResponseMessage = "";
        try {
            signedResponseMessage = this.algorithm.encryptMessage(this.secretKey,
                signableResponseMessage);
        } catch(SignatureException e) {
            throw new IOException("Fail to sign response message", e);
        }

        if (serverSignature.compareTo(signedResponseMessage) != 0) {
            throw new HttpException("Error: Invalid server response validation.");
        }
    }

}
