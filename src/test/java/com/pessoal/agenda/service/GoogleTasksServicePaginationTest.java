package com.pessoal.agenda.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleTasksServicePaginationTest {

    @Test
    void listTasksReadsEveryPageBeyondOneHundredItems() throws Exception {
        List<String> paths = new ArrayList<>();
        GoogleTasksService service = new GoogleTasksService(request -> {
            paths.add(request.path());
            String body = request.path().contains("pageToken=second-page")
                    ? taskPage(100, 50, null)
                    : taskPage(0, 100, "second-page");
            return new GoogleTasksService.ApiResponse(200, body);
        });

        var tasks = service.listTasks("list with spaces", true);

        assertEquals(150, tasks.size());
        assertEquals("task-0", tasks.getFirst().id());
        assertEquals("task-149", tasks.getLast().id());
        assertEquals(2, paths.size());
        assertTrue(paths.getFirst().contains("maxResults=100"));
        assertTrue(paths.getFirst().contains("showDeleted=false"));
        assertTrue(paths.getLast().contains("pageToken=second-page"));
    }

    private static String taskPage(int start, int count, String nextPageToken) {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) json.append(',');
            int id = start + index;
            json.append("{\"id\":\"task-").append(id)
                    .append("\",\"title\":\"Task ").append(id)
                    .append("\",\"status\":\"needsAction\"}");
        }
        json.append(']');
        if (nextPageToken != null) {
            json.append(",\"nextPageToken\":\"").append(nextPageToken).append('"');
        }
        return json.append('}').toString();
    }
}
