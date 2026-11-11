package online.aireport.split.data.api

import online.aireport.split.data.model.AuthResponse
import online.aireport.split.data.model.ChargeRequest
import online.aireport.split.data.model.CreateGroupRequest
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseList
import online.aireport.split.data.model.ExpenseRequest
import online.aireport.split.data.model.FineRequest
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.GroupList
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.data.model.JoinRequest
import online.aireport.split.data.model.LoginRequest
import online.aireport.split.data.model.MemberNameRequest
import online.aireport.split.data.model.ReceiptScan
import online.aireport.split.data.model.ReceiptScanRequest
import online.aireport.split.data.model.RegisterRequest
import online.aireport.split.data.model.ServerConfig
import online.aireport.split.data.model.SettlementRequest
import online.aireport.split.data.model.UpdateGroupRequest
import online.aireport.split.data.model.UpdateProfileRequest
import online.aireport.split.data.model.User
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** The Split Sport REST API, see `docs/api.md`. Paths are relative to `<server>/api/`. */
interface SplitApi {
    @GET("config")
    suspend fun config(): ServerConfig

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("me")
    suspend fun me(): User

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): User

    @DELETE("me")
    suspend fun deleteMe()

    @GET("groups")
    suspend fun groups(): GroupList

    @POST("groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): GroupDetail

    @GET("groups/{groupId}")
    suspend fun group(@Path("groupId") groupId: String): GroupDetail

    @PATCH("groups/{groupId}")
    suspend fun updateGroup(@Path("groupId") groupId: String, @Body body: UpdateGroupRequest): GroupDetail

    @POST("groups/{groupId}/fines")
    suspend fun addFine(@Path("groupId") groupId: String, @Body body: FineRequest): GroupDetail

    @PUT("groups/{groupId}/fines/{fineId}")
    suspend fun updateFine(
        @Path("groupId") groupId: String,
        @Path("fineId") fineId: String,
        @Body body: FineRequest,
    ): GroupDetail

    @DELETE("groups/{groupId}/fines/{fineId}")
    suspend fun removeFine(@Path("groupId") groupId: String, @Path("fineId") fineId: String): GroupDetail

    @POST("groups/{groupId}/leave")
    suspend fun leaveGroup(@Path("groupId") groupId: String)

    @POST("groups/{groupId}/members")
    suspend fun addMember(@Path("groupId") groupId: String, @Body body: MemberNameRequest): GroupDetail

    @PATCH("groups/{groupId}/members/{memberId}")
    suspend fun renameMember(
        @Path("groupId") groupId: String,
        @Path("memberId") memberId: String,
        @Body body: MemberNameRequest,
    ): GroupDetail

    @DELETE("groups/{groupId}/members/{memberId}")
    suspend fun removeMember(@Path("groupId") groupId: String, @Path("memberId") memberId: String): GroupDetail

    @GET("invites/{code}")
    suspend fun invite(@Path("code") code: String): InvitePreview

    @POST("invites/{code}/join")
    suspend fun join(@Path("code") code: String, @Body body: JoinRequest): GroupDetail

    @GET("groups/{groupId}/expenses")
    suspend fun expenses(@Path("groupId") groupId: String): ExpenseList

    @POST("groups/{groupId}/expenses")
    suspend fun createExpense(@Path("groupId") groupId: String, @Body body: ExpenseRequest): Expense

    @GET("groups/{groupId}/expenses/{expenseId}")
    suspend fun expense(@Path("groupId") groupId: String, @Path("expenseId") expenseId: String): Expense

    @PUT("groups/{groupId}/expenses/{expenseId}")
    suspend fun updateExpense(
        @Path("groupId") groupId: String,
        @Path("expenseId") expenseId: String,
        @Body body: ExpenseRequest,
    ): Expense

    @DELETE("groups/{groupId}/expenses/{expenseId}")
    suspend fun deleteExpense(@Path("groupId") groupId: String, @Path("expenseId") expenseId: String)

    @POST("groups/{groupId}/charges")
    suspend fun createCharge(@Path("groupId") groupId: String, @Body body: ChargeRequest): Expense

    @POST("groups/{groupId}/settlements")
    suspend fun createSettlement(@Path("groupId") groupId: String, @Body body: SettlementRequest): Expense

    @POST("receipts/scan")
    suspend fun scanReceipt(@Body body: ReceiptScanRequest): ReceiptScan
}
