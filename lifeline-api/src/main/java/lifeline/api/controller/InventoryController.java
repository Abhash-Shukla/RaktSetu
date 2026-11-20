package lifeline.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lifeline.api.dto.BankInventoryDto;
import lifeline.api.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<BankInventoryDto>> getAllInventories() {
        return ResponseEntity.ok(inventoryService.getAllInventories());
    }

    @GetMapping("/banks/{bankId}")
    public ResponseEntity<List<BankInventoryDto>> getInventoryByBank(@PathVariable("bankId") Long bankId) {
        return ResponseEntity.ok(inventoryService.getInventoryByBank(bankId));
    }

    @PutMapping("/banks/{bankId}")
    public ResponseEntity<BankInventoryDto> updateInventory(
        @PathVariable("bankId") Long bankId,
        @RequestParam("bloodGroup") String bloodGroup,
        @RequestParam("units") int units,
        Authentication auth,
        HttpServletRequest request
    ) {
        String username = auth.getName();
        String ip = request.getRemoteAddr();
        BankInventoryDto updated = inventoryService.updateInventory(bankId, bloodGroup, units, username, ip);
        return ResponseEntity.ok(updated);
    }
}
