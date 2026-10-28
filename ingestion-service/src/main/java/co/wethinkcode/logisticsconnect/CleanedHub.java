package co.wethinkcode.logisticsconnect;

import java.util.List;

public record CleanedHub(
        String hubId,
        String province,
        String sortingCenter,
        Boolean active,
        List<String> aliases
) {
}
