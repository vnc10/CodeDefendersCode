package org.codedefenders.beans.game;

import javax.annotation.ManagedBean;
import javax.enterprise.context.SessionScoped;
import java.io.Serializable;
import java.util.List;

/**
 * <p>Saves the previous mutant / test submission so the code of the previous submission can be displayed. </p>
 * <p>Bean Name: {@code previousSubmission}</p>
 */
@ManagedBean
@SessionScoped
// TODO: Put error message here and show it somewhere different than the messages?
public class OldCode implements Serializable {
    /**
     * The
     */
    private String testCode;

    public OldCode() {
        testCode = "";
    }

    public void setTestCode(String testCode) {
        this.testCode = testCode;
    }

    public void clear() {
        testCode = "";
    }

    public boolean hasTest() {
        return testCode != null;
    }

    public String getTestCode() {
        return testCode;
    }

}
