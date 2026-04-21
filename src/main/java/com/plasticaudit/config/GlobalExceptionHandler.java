package com.plasticaudit.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

/**
 * Global exception handler — catches unhandled exceptions and maps them to
 * appropriate HTTP responses instead of leaking stack traces to the user.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 404 — Handles unknown URL paths.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ModelAndView handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] 404 Not Found: {}", request.getRequestURI());
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("errorMsg", "The page '" + request.getRequestURI() + "' was not found.");
        mav.addObject("path", request.getRequestURI());
        return mav;
    }

    /**
     * Maps IllegalArgumentException (e.g. "Waste entry not found: 99")
     * to an HTTP 400/404 page instead of a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("[GlobalExceptionHandler] Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("errorMsg", ex.getMessage());
        mav.addObject("path", request.getRequestURI());
        return mav;
    }

    /**
     * Catches RuntimeException (e.g. "Report not found") and serves a user-friendly
     * error page.
     */
    @ExceptionHandler(RuntimeException.class)
    public ModelAndView handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("[GlobalExceptionHandler] Runtime error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("errorMsg", "An unexpected error occurred. Please try again or contact support.");
        mav.addObject("path", request.getRequestURI());
        return mav;
    }

    /**
     * REST API endpoints — return JSON error instead of HTML.
     */
    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("[GlobalExceptionHandler] Unhandled exception on {}", request.getRequestURI(), ex);

        // If it looks like a REST request, return JSON
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error", "message", ex.getMessage()));
        }

        // Otherwise render HTML error page
        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("errorMsg", "Something went wrong. Please try again.");
        mav.addObject("path", request.getRequestURI());
        return mav;
    }
}
