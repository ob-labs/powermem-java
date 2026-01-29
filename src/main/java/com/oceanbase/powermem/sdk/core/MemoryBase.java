package com.oceanbase.powermem.sdk.core;

import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.AddMemoryResponse;
import com.oceanbase.powermem.sdk.model.DeleteAllMemoriesRequest;
import com.oceanbase.powermem.sdk.model.DeleteAllMemoriesResponse;
import com.oceanbase.powermem.sdk.model.DeleteMemoryResponse;
import com.oceanbase.powermem.sdk.model.GetAllMemoriesRequest;
import com.oceanbase.powermem.sdk.model.GetAllMemoriesResponse;
import com.oceanbase.powermem.sdk.model.GetMemoryRequest;
import com.oceanbase.powermem.sdk.model.GetMemoryResponse;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesResponse;
import com.oceanbase.powermem.sdk.model.UpdateMemoryRequest;
import com.oceanbase.powermem.sdk.model.UpdateMemoryResponse;

/**
 * Common memory operations shared by synchronous and asynchronous memory managers.
 *
 * <p>Python reference: {@code src/powermem/core/memory.py} and {@code src/powermem/core/async_memory.py}</p>
 */
public interface MemoryBase {

    AddMemoryResponse add(AddMemoryRequest request);

    SearchMemoriesResponse search(SearchMemoriesRequest request);

    UpdateMemoryResponse update(UpdateMemoryRequest request);

    GetMemoryResponse get(GetMemoryRequest request);

    DeleteMemoryResponse delete(String memoryId, String userId, String agentId);

    GetAllMemoriesResponse getAll(GetAllMemoriesRequest request);

    DeleteAllMemoriesResponse deleteAll(DeleteAllMemoriesRequest request);
}
