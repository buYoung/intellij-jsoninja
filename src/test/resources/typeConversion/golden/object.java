import java.util.List;

public class RootAddres {
    private String zipCode;

    public String getZipCode() {
        return zipCode;
    }
    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }
}

public class Root {
    private Boolean active;

    private RootAddres address;

    private Integer id;

    private String name;

    private List<Integer> scores;

    public Boolean getActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }

    public RootAddres getAddress() {
        return address;
    }
    public void setAddress(RootAddres address) {
        this.address = address;
    }

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getScores() {
        return scores;
    }
    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }
}
