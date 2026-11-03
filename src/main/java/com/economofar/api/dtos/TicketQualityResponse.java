package com.economofar.api.dtos;

import java.util.List;

/**
 * The saved tickets of the last months that have a problem the analyses cannot cope with.
 *
 * from and to are the first and the last day of the period (yyyy-MM-dd), examined is the number of
 * tickets looked at (a ticket dated after today is looked at too), flaggedCount the number of
 * tickets with at least one problem. counts always has the 5 kinds of problem, in a fixed order,
 * with the number of tickets concerned (a ticket can have several problems). tickets lists at most
 * 100 of the flagged tickets, the most recent first.
 */
public record TicketQualityResponse(
        int months,
        String from,
        String to,
        int examined,
        int flaggedCount,
        List<QualityIssueCountResponse> counts,
        List<QualityTicketResponse> tickets
) {
}
