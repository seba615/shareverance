package com.shareverance;

import com.shareverance.service.*;
import com.shareverance.repository.*;
import com.shareverance.repository.jdbc.JdbcIndividualSummaryRepository;
import com.shareverance.model.UserOrganization;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class IndividualSummaryServiceTest {
    final AuthService auth = mock(AuthService.class);
    final UserOrganizationRepository memberships = mock(UserOrganizationRepository.class);
    final JdbcIndividualSummaryRepository repository = mock(JdbcIndividualSummaryRepository.class);
    final IndividualSummaryService service = new IndividualSummaryService(auth, memberships, repository);
    @Test void rejectsForeignOrganizationBeforeReadingAmounts() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.summarize(1L, 2L, null, null)).getStatusCode().value());
        verifyNoInteractions(repository, memberships);
    }
    @Test void rejectsInvertedDates() {
        when(auth.isOrganizationMember(1L, 2L)).thenReturn(true);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.summarize(1L, 2L,
                LocalDate.of(2024,2,1), LocalDate.of(2024,1,1))).getStatusCode().value());
        verifyNoInteractions(repository);
    }
    @Test void rejectsMissingOrInactiveMembership() {
        when(auth.isOrganizationMember(1L, 2L)).thenReturn(true);
        when(memberships.findByUserIdAndOrganizationId(1L, 2L)).thenReturn(Optional.empty());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.summarize(1L, 2L, null, null)).getStatusCode().value());
        verifyNoInteractions(repository);
    }
}
