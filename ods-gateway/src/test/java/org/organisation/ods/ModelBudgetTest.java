package org.organisation.ods;

import org.junit.jupiter.api.Test;
import org.organisation.ods.ai.ModelBudget;
import org.organisation.ods.ai.ModelSelection;
import org.organisation.ods.contracts.OdsException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Checks signed per-model context estimates and output ceilings. Exigences : DEVAI-065, DEVAI-193, DEVAI-194. */
class ModelBudgetTest {
    /** Rejects an input budget that leaves insufficient room for model output. Exigences : DEVAI-065, DEVAI-193. */
    @Test void enforcesContextAndOutputLimits() {
        var model=new ModelSelection("OPENAI","synthetic-model","generic","openai","https://api.openai.com/v1","ODS_OPENAI_API_KEY",true,true,false,false,true,1000,1000,100);
        var messages=List.of(Map.<String,Object>of("role","user","content","synthetic context"));
        long input=ModelBudget.estimateInputTokens(messages);
        assertTrue(input>0);
        assertEquals(100,ModelBudget.outputLimit(model,4000));
        assertDoesNotThrow(()->ModelBudget.requireFits(model,input,100));
        assertEquals("CONTEXT_REJECTED",assertThrows(OdsException.class,()->ModelBudget.requireFits(model,950,100)).code());
        assertEquals("CONTEXT_REJECTED",assertThrows(OdsException.class,()->ModelBudget.requireFits(model,input,101)).code());
    }
}
