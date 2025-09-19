package com.sqlparser.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlparser.model.ExtractTablesRequest;
import com.sqlparser.model.ReplaceTablesRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.sqlparser.service.SqlParserService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.HashSet;

@WebMvcTest(controllers = SqlParserController.class)
public class SqlParserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SqlParserService sqlParserService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/sql/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("SQL Parser Server is running"));
    }

    @Test
    public void testExtractTables() throws Exception {
        ExtractTablesRequest request = new ExtractTablesRequest("SELECT * FROM users JOIN orders ON users.id = orders.user_id");

        Set<String> mockResult = new HashSet<>();
        mockResult.add("users");
        mockResult.add("orders");
        when(sqlParserService.extractTableNames(anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/sql/extract-tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tableNames").isArray());
    }

    @Test
    public void testReplaceTables() throws Exception {
        Map<String, String> tableMapping = new HashMap<>();
        tableMapping.put("users", "user_table");
        tableMapping.put("orders", "order_table");

        ReplaceTablesRequest request = new ReplaceTablesRequest(
                "SELECT * FROM users JOIN orders ON users.id = orders.user_id",
                tableMapping
        );

        when(sqlParserService.replaceTableNames(anyString(), any(Map.class)))
                .thenReturn("SELECT * FROM user_table JOIN order_table ON user_table.id = order_table.user_id");

        mockMvc.perform(post("/api/sql/replace-tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sql").isString());
    }

    @Test
    public void testExtractTablesWithEmptySQL() throws Exception {
        ExtractTablesRequest request = new ExtractTablesRequest("");

        when(sqlParserService.extractTableNames(anyString())).thenThrow(new RuntimeException("Empty SQL"));

        mockMvc.perform(post("/api/sql/extract-tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}