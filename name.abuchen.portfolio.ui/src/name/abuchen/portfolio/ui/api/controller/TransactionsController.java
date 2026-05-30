package name.abuchen.portfolio.ui.api.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.api.dto.TransactionDto;
import name.abuchen.portfolio.ui.api.dto.TransactionMutationDto;
import name.abuchen.portfolio.ui.api.service.TransactionManagementService;

/**
 * REST Controller for transaction operations.
 * 
 * This controller provides endpoints to manage all transactions (account and portfolio) within a portfolio.
 */
@Path("/api/v1/portfolios/{portfolioId}/transactions")
public class TransactionsController extends BaseController {
    
    /**
     * Get all transactions in a portfolio.
     * Returns de-duplicated transactions from both accounts and portfolios.
     * 
     * @param portfolioId The portfolio ID
     * @return List of all transactions
     */
    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllTransactions(@PathParam("portfolioId") String portfolioId) {
        try {
            logger.info("Getting all transactions for portfolio: {}", portfolioId);
            
            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before accessing transactions");
            }

            List<TransactionDto> transactions = convertTransactionsToDto(client.getAllTransactions());
            
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("count", transactions.size());
            response.put("transactions", transactions);
            
            logger.info("Returning {} transactions for portfolio {}", transactions.size(), portfolioId);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error getting transactions for portfolio {}: {}", 
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Get a specific transaction by UUID.
     * 
     * @param portfolioId The portfolio ID
     * @param transactionUuid The transaction UUID
     * @return Transaction details
     */
    @GET
    @Path("/{transactionUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTransactionById(@PathParam("portfolioId") String portfolioId,
                                      @PathParam("transactionUuid") String transactionUuid) {
        try {
            logger.info("Getting transaction {} for portfolio {}", transactionUuid, portfolioId);
            
            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before accessing transactions");
            }

            TransactionPair<?> txPair = TransactionManagementService.findTransaction(client, transactionUuid);
            TransactionDto transactionDto = convertTransactionPairToDto(txPair);
            
            Map<String, Object> response = new HashMap<>();
            response.put("portfolioId", portfolioId);
            response.put("transaction", transactionDto);
            
            logger.info("Returning transaction {} for portfolio {}", transactionUuid, portfolioId);
            
            return Response.ok(response).build();
            
        } catch (NoSuchElementException e) {
            logger.warn("Transaction not found: {} in portfolio: {}", transactionUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Transaction not found",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error getting transaction {} for portfolio {}: {}", 
                transactionUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, 
                "Internal server error", 
                e.getMessage());
        }
    }
    
    /**
     * Create a new transaction.
     * 
     * @param portfolioId The portfolio ID
     * @param transactionData Transaction data
     * @return Created transaction
     */
    @POST
    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTransaction(@PathParam("portfolioId") String portfolioId,
                                      TransactionMutationDto transactionData) {
        try {
            logger.info("Creating transaction for portfolio {}", portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before creating transactions");
            }

            TransactionPair<?> txPair = TransactionManagementService.createTransaction(client, transactionData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            TransactionDto transactionDto = convertTransactionPairToDto(txPair);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("transaction", transactionDto);
            response.put("message", "Transaction created successfully");

            logger.info("Created transaction {} for portfolio {}", transactionDto.getUuid(), portfolioId);

            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Referenced entity not found while creating transaction for portfolio {}: {}",
                portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Referenced entity not found",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid transaction creation request for portfolio {}: {}", portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating transaction for portfolio {}: {}",
                portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Update an existing transaction.
     * 
     * @param portfolioId The portfolio ID
     * @param transactionUuid The transaction UUID
     * @param transactionData Updated transaction data
     * @return Updated transaction
     */
    @PUT
    @Path("/{transactionUuid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateTransaction(@PathParam("portfolioId") String portfolioId,
                                      @PathParam("transactionUuid") String transactionUuid,
                                      TransactionMutationDto transactionData) {
        try {
            logger.info("Updating transaction {} for portfolio {}", transactionUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before updating transactions");
            }

            TransactionPair<?> txPair = TransactionManagementService.updateTransaction(client, transactionUuid,
                transactionData);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            TransactionDto transactionDto = convertTransactionPairToDto(txPair);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("transaction", transactionDto);
            response.put("message", "Transaction updated successfully");

            logger.info("Updated transaction {} for portfolio {}", transactionUuid, portfolioId);

            return Response.ok(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Transaction not found: {} in portfolio: {}", transactionUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Transaction not found",
                e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid transaction update request for transaction {} in portfolio {}: {}",
                transactionUuid, portfolioId, e.getMessage());
            return createErrorResponse(Response.Status.BAD_REQUEST,
                "Invalid request",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error updating transaction {} for portfolio {}: {}",
                transactionUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }
    
    /**
     * Delete a transaction.
     * 
     * @param portfolioId The portfolio ID
     * @param transactionUuid The transaction UUID
     * @return Deletion confirmation
     */
    @DELETE
    @Path("/{transactionUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteTransaction(@PathParam("portfolioId") String portfolioId,
                                      @PathParam("transactionUuid") String transactionUuid) {
        try {
            logger.info("Deleting transaction {} for portfolio {}", transactionUuid, portfolioId);

            Client client = portfolioFileService.getPortfolio(portfolioId);

            if (client == null) {
                logger.warn("No cached client found for portfolio: {}", portfolioId);
                return createPreconditionRequiredResponse(
                    "PORTFOLIO_NOT_LOADED",
                    "Portfolio must be opened first before deleting transactions");
            }

            TransactionPair<?> txPair = TransactionManagementService.deleteTransaction(client, transactionUuid);
            client.markDirty();
            portfolioFileService.saveFile(portfolioId);

            var transaction = txPair.getTransaction();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("portfolioId", portfolioId);
            response.put("transactionUuid", transactionUuid);
            response.put("transactionType", txPair.getOwner() instanceof name.abuchen.portfolio.model.Account
                ? "ACCOUNT" : "PORTFOLIO");
            response.put("type", transaction instanceof AccountTransaction
                ? ((AccountTransaction) transaction).getType().name()
                : ((PortfolioTransaction) transaction).getType().name());
            response.put("message", "Transaction deleted successfully");

            logger.info("Deleted transaction {} for portfolio {}", transactionUuid, portfolioId);

            return Response.ok(response).build();

        } catch (NoSuchElementException e) {
            logger.warn("Transaction not found: {} in portfolio: {}", transactionUuid, portfolioId);
            return createErrorResponse(Response.Status.NOT_FOUND,
                "Transaction not found",
                e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error deleting transaction {} for portfolio {}: {}",
                transactionUuid, portfolioId, e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage());
        }
    }

    /**
     * Helper method to convert a list of TransactionPair objects to TransactionDto list.
     */
    private List<TransactionDto> convertTransactionsToDto(List<TransactionPair<?>> transactionPairs) {
        List<TransactionDto> transactionDtos = new ArrayList<>();
        
        for (TransactionPair<?> pair : transactionPairs) {
            transactionDtos.add(convertTransactionPairToDto(pair));
        }
        
        transactionDtos.sort((t1, t2) -> t2.getDateTime().compareTo(t1.getDateTime()));
        
        return transactionDtos;
    }
    
    /**
     * Helper method to convert a single TransactionPair to TransactionDto.
     */
    private TransactionDto convertTransactionPairToDto(TransactionPair<?> pair) {
        TransactionDto dto = new TransactionDto();
        
        var transaction = pair.getTransaction();
        
        dto.setUuid(transaction.getUUID());
        dto.setDateTime(transaction.getDateTime());
        dto.setCurrencyCode(transaction.getCurrencyCode());
        dto.setAmount(transaction.getAmount() / Values.Amount.divider());
        dto.setNote(transaction.getNote());
        dto.setSource(transaction.getSource());
        dto.setUpdatedAt(transaction.getUpdatedAt());
        
        if (transaction.getSecurity() != null) {
            dto.setSecurityUuid(transaction.getSecurity().getUUID());
            dto.setSecurityName(transaction.getSecurity().getName());
        }
        
        dto.setShares(transaction.getShares() / (double) Values.Share.factor());
        
        if (pair.getOwner() instanceof name.abuchen.portfolio.model.Account) {
            name.abuchen.portfolio.model.Account account = (name.abuchen.portfolio.model.Account) pair.getOwner();
            dto.setOwnerUuid(account.getUUID());
            dto.setOwnerName(account.getName());
            dto.setTransactionType("ACCOUNT");
            
            if (transaction instanceof AccountTransaction) {
                AccountTransaction at = (AccountTransaction) transaction;
                dto.setType(at.getType().name());
            }
        } else if (pair.getOwner() instanceof name.abuchen.portfolio.model.Portfolio) {
            name.abuchen.portfolio.model.Portfolio portfolio = (name.abuchen.portfolio.model.Portfolio) pair.getOwner();
            dto.setOwnerUuid(portfolio.getUUID());
            dto.setOwnerName(portfolio.getName());
            dto.setTransactionType("PORTFOLIO");
            
            if (transaction instanceof PortfolioTransaction) {
                PortfolioTransaction pt = (PortfolioTransaction) transaction;
                dto.setType(pt.getType().name());
            }
        }
        
        return dto;
    }
}
