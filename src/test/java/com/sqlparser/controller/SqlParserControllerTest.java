package com.sqlparser.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlparser.model.ExtractTablesRequest;
import com.sqlparser.model.ReplaceTablesRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureTestMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestMvc
public class SqlParserControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

        mockMvc.perform(post("/api/sql/extract-tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpected(jsonPath("$.success").value(false));
    }
}