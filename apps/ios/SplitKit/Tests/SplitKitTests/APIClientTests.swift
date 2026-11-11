import XCTest
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import SplitKit

/// Answers every request with a canned response and keeps the requests it saw.
private final class StubTransport: HTTPTransport, @unchecked Sendable {
    var status = 200
    var body = "{}"
    private(set) var requests: [URLRequest] = []

    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        requests.append(request)
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
        return (Data(body.utf8), response)
    }

    var last: URLRequest { requests[requests.count - 1] }

    func lastJSON() throws -> NSDictionary {
        try XCTUnwrap(JSONSerialization.jsonObject(with: XCTUnwrap(last.httpBody)) as? NSDictionary)
    }
}

/// Counts calls from any thread.
private final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0

    func increment() {
        lock.lock()
        count += 1
        lock.unlock()
    }

    var value: Int {
        lock.lock()
        defer { lock.unlock() }
        return count
    }
}

final class APIClientTests: XCTestCase {
    private let transport = StubTransport()
    private let expired = Counter()

    private func client(token: String? = "secret") -> APIClient {
        let expired = expired
        return APIClient(
            serverURL: URL(string: "https://split.example.nl")!, token: token, transport: transport,
            onSessionExpired: { expired.increment() }
        )
    }

    private let groupJSON = """
        {"id":"g1","name":"Dames 2","color":"teal","sport":"hockey","club":"MHC Oostpoort","season":"2026/27","currency":"EUR",
         "inviteCode":"ABCD2345","myMemberId":"m1","potMemberId":"p1","treasurerMemberId":"m1",
         "members":[{"id":"m1","name":"Anna","kind":"player","userId":"u1","iban":"NL91ABNA0417164300"},
                    {"id":"m2","name":"Bas","kind":"player","userId":null,"iban":null},
                    {"id":"p1","name":"Teamkas","kind":"pot","userId":null,"iban":null}],
         "balances":[{"memberId":"m1","balanceCents":500,"potCents":0,"peerCents":500},
                     {"memberId":"m2","balanceCents":-700,"potCents":-200,"peerCents":-500},
                     {"memberId":"p1","balanceCents":200,"potCents":200,"peerCents":0}],
         "transfers":[{"fromMemberId":"m2","toMemberId":"p1","amountCents":200},{"fromMemberId":"m2","toMemberId":"m1","amountCents":500}],
         "pot":{"cashCents":4750,"toReceiveCents":200,"toPayCents":0},
         "fines":[{"id":"f1","name":"Te laat","amountCents":200}],
         "fineStats":[{"memberId":"m2","count":1,"totalCents":200}],
         "totalSpentCents":1000}
        """

    func testSendsTheTokenAndDecodesATeam() async throws {
        transport.body = groupJSON
        let group = try await client().group(id: "g1")

        XCTAssertEqual(transport.last.url?.absoluteString, "https://split.example.nl/api/groups/g1")
        XCTAssertEqual(transport.last.httpMethod, "GET")
        XCTAssertEqual(transport.last.value(forHTTPHeaderField: "Authorization"), "Bearer secret")
        XCTAssertNil(transport.last.httpBody)
        XCTAssertEqual(group.color, .teal)
        XCTAssertEqual(group.sport, .hockey)
        XCTAssertEqual(group.club, "MHC Oostpoort")
        XCTAssertEqual(group.season, "2026/27")
        XCTAssertEqual(group.members.map(\.name), ["Anna", "Bas", "Teamkas"])
        XCTAssertEqual(group.members.map(\.kind), [.player, .player, .pot])
        XCTAssertEqual(group.players.map(\.id), ["m1", "m2"])
        XCTAssertEqual(group.potMemberId, "p1")
        XCTAssertEqual(group.treasurerMemberId, "m1")
        XCTAssertNil(group.members[1].userId)
        XCTAssertEqual(group.transfers.first, Transfer(fromMemberId: "m2", toMemberId: "p1", amountCents: 200))
        XCTAssertEqual(group.balance(of: "m2"), Balance(memberId: "m2", balanceCents: -700, potCents: -200, peerCents: -500))
        XCTAssertEqual(group.pot, PotStatus(cashCents: 4750, toReceiveCents: 200, toPayCents: 0))
        XCTAssertEqual(group.fines, [Fine(id: "f1", name: "Te laat", amountCents: 200)])
        XCTAssertEqual(group.fineStats, [FineStat(memberId: "m2", count: 1, totalCents: 200)])
        XCTAssertEqual(group.parties.map(\.highlighter), [.amber, .rose, nil])
    }

    func testValuesFromANewerServerDoNotFailDecoding() async throws {
        transport.body = groupJSON
            .replacingOccurrences(of: #""sport":"hockey""#, with: #""sport":"schaken""#)
            .replacingOccurrences(of: #""kind":"player","userId":null"#, with: #""kind":"trainer","userId":null"#)
            .replacingOccurrences(of: #""club":"MHC Oostpoort","season":"2026/27""#, with: #""club":null,"season":null"#)
            .replacingOccurrences(of: #""treasurerMemberId":"m1""#, with: #""treasurerMemberId":null"#)
        let group = try await client().group(id: "g1")

        XCTAssertEqual(group.sport, .overig, "An unknown sport falls back to overig")
        XCTAssertEqual(group.members[1].kind, .player, "Anything that is not the pot is a person")
        XCTAssertNil(group.club)
        XCTAssertNil(group.season)
        XCTAssertNil(group.treasurerMemberId)
    }

    func testDecodesTeamSummariesAndInvites() async throws {
        transport.body = """
            {"groups":[{"id":"g1","name":"Dames 2","color":"rose","sport":"hockey","club":"MHC Oostpoort","season":"2026/27",
              "currency":"EUR","memberCount":5,"myMemberId":"m1","myBalanceCents":-250,"lastActivityAt":"2026-11-09T10:00:00.000Z"},
             {"id":"g2","name":"Zaal","color":"lime","sport":"voetbal","club":null,"season":null,
              "currency":"EUR","memberCount":1,"myMemberId":"m9","myBalanceCents":0,"lastActivityAt":"2026-11-08T10:00:00.000Z"}]}
            """
        let groups = try await client().groups()
        XCTAssertEqual(groups.map(\.sport), [.hockey, .voetbal])
        XCTAssertEqual(groups.map(\.club), ["MHC Oostpoort", nil])
        XCTAssertEqual(groups.map(\.memberCount), [5, 1])

        transport.body = """
            {"groupId":"g1","name":"Dames 2","color":"rose","sport":"hockey","club":"MHC Oostpoort","memberCount":5,
             "alreadyMember":false,"unclaimedMembers":[{"id":"m4","name":"Lotte"}]}
            """
        let preview = try await client().invite(code: "ABCD2345")
        XCTAssertEqual(preview.sport, .hockey)
        XCTAssertEqual(preview.club, "MHC Oostpoort")
        XCTAssertEqual(preview.unclaimedMembers.map(\.name), ["Lotte"])
    }

    func testDecodesExpensesOfEveryKind() async throws {
        transport.body = """
            {"expenses":[
              {"id":"e1","groupId":"g1","kind":"expense","description":"Pizza","amountCents":2300,"paidById":"m1","date":"2026-11-02",
               "splitMode":"items","shares":[{"memberId":"m1","amountCents":1300,"weight":null},{"memberId":"m2","amountCents":1000,"weight":null}],
               "items":[{"name":"Margherita","amountCents":1100,"memberIds":["m1"]},{"name":"Funghi","amountCents":900,"memberIds":["m2"]}],
               "createdAt":"2026-11-02T10:00:00.000Z","updatedAt":"2026-11-02T10:00:00.000Z"},
              {"id":"e2","groupId":"g1","kind":"settlement","description":"Betaling","amountCents":500,"paidById":"m2","date":"2026-11-03",
               "splitMode":"exact","shares":[{"memberId":"m1","amountCents":500,"weight":null}],"items":[],
               "createdAt":"2026-11-03T10:00:00.000Z","updatedAt":"2026-11-03T10:00:00.000Z"},
              {"id":"e3","groupId":"g1","kind":"expense","description":"Huur","amountCents":900,"paidById":"m1","date":"2026-11-01",
               "splitMode":"shares","shares":[{"memberId":"m1","amountCents":600,"weight":2},{"memberId":"m2","amountCents":300,"weight":1}],"items":[],
               "createdAt":"2026-11-01T10:00:00.000Z","updatedAt":"2026-11-01T10:00:00.000Z"},
              {"id":"e4","groupId":"g1","kind":"fine","description":"Te laat","amountCents":200,"paidById":"p1","date":"2026-11-01",
               "splitMode":"exact","shares":[{"memberId":"m2","amountCents":200,"weight":null}],"items":[],
               "createdAt":"2026-11-01T10:00:00.000Z","updatedAt":"2026-11-01T10:00:00.000Z"},
              {"id":"e5","groupId":"g1","kind":"contribution","description":"Inleg november","amountCents":5000,"paidById":"p1","date":"2026-11-01",
               "splitMode":"exact","shares":[{"memberId":"m1","amountCents":2500,"weight":null},{"memberId":"m2","amountCents":2500,"weight":null}],"items":[],
               "createdAt":"2026-11-01T10:00:00.000Z","updatedAt":"2026-11-01T10:00:00.000Z"}
            ]}
            """
        let expenses = try await client().expenses(groupId: "g1")

        XCTAssertEqual(expenses.map(\.kind), [.expense, .settlement, .expense, .fine, .contribution])
        XCTAssertEqual(expenses[3].shares.map(\.memberId), ["m2"])
        XCTAssertEqual(expenses[4].amountCents, 5000)
        XCTAssertEqual(expenses[0].date, CalendarDay(year: 2026, month: 11, day: 2))
        XCTAssertEqual(expenses[0].items.map(\.name), ["Margherita", "Funghi"])
        XCTAssertEqual(expenses[2].shares.map(\.weight), [2, 1])
        XCTAssertEqual(expenses[0].impactCents(on: "m1"), 1000)
        XCTAssertEqual(expenses[0].impactCents(on: "m2"), -1000)
        XCTAssertNil(expenses[0].impactCents(on: "m3"))
    }

    func testPostsAnExpenseAsJSON() async throws {
        transport.status = 201
        transport.body = """
            {"id":"e1","groupId":"g1","kind":"expense","description":"Boodschappen","amountCents":1250,"paidById":"m1","date":"2026-11-02",
             "splitMode":"equal","shares":[],"items":[],"createdAt":"x","updatedAt":"x"}
            """
        let input = ExpenseInput(
            description: "Boodschappen", amountCents: 1250, date: CalendarDay(year: 2026, month: 11, day: 2),
            paidById: "m1", split: .equal(memberIds: ["m1", "m2"])
        )
        _ = try await client().createExpense(groupId: "g1", input: input)

        XCTAssertEqual(transport.last.httpMethod, "POST")
        XCTAssertEqual(transport.last.url?.path, "/api/groups/g1/expenses")
        XCTAssertEqual(transport.last.value(forHTTPHeaderField: "Content-Type"), "application/json")
        let expected: NSDictionary = [
            "description": "Boodschappen", "amountCents": 1250, "date": "2026-11-02", "paidById": "m1",
            "split": ["mode": "equal", "memberIds": ["m1", "m2"]],
        ]
        XCTAssertEqual(try transport.lastJSON(), expected)
    }

    func testLeavesOutFieldsThatDoNotChange() async throws {
        transport.body = groupJSON
        _ = try await client().updateGroup(id: "g1", patch: GroupPatch(color: .rose))
        XCTAssertEqual(try transport.lastJSON(), ["color": "rose"])
        XCTAssertEqual(transport.last.httpMethod, "PATCH")

        _ = try await client().updateGroup(id: "g1", patch: GroupPatch(name: "Dames 3", club: "", season: "2027/28"))
        XCTAssertEqual(try transport.lastJSON(), ["name": "Dames 3", "club": "", "season": "2027/28"], "An empty club clears it")

        _ = try await client().updateGroup(id: "g1", patch: GroupPatch(sport: .korfbal, treasurerMemberId: "m2"))
        XCTAssertEqual(try transport.lastJSON(), ["sport": "korfbal", "treasurerMemberId": "m2"])

        _ = try await client().joinGroup(code: "ABCD2345", memberId: nil)
        XCTAssertEqual(try transport.lastJSON(), [:])
        XCTAssertEqual(transport.last.url?.path, "/api/invites/ABCD2345/join")
    }

    func testCreatesATeamWithSportClubAndSeason() async throws {
        transport.status = 201
        transport.body = groupJSON
        _ = try await client().createGroup(name: "Dames 2", color: nil, sport: .hockey, club: "MHC Oostpoort", season: "2026/27")
        XCTAssertEqual(transport.last.httpMethod, "POST")
        XCTAssertEqual(transport.last.url?.path, "/api/groups")
        XCTAssertEqual(try transport.lastJSON(), ["name": "Dames 2", "sport": "hockey", "club": "MHC Oostpoort", "season": "2026/27"])
    }

    func testPostsAChargePerMember() async throws {
        transport.status = 201
        transport.body = """
            {"id":"e9","groupId":"g1","kind":"fine","description":"Te laat","amountCents":400,"paidById":"p1","date":"2026-11-02",
             "splitMode":"exact","shares":[],"items":[],"createdAt":"x","updatedAt":"x"}
            """
        let input = ChargeInput(
            type: .fine, description: "Te laat", amountCents: 200, memberIds: ["m1", "m2"],
            date: CalendarDay(year: 2026, month: 11, day: 2)
        )
        let expense = try await client().createCharge(groupId: "g1", input: input)

        XCTAssertEqual(expense.kind, .fine)
        XCTAssertEqual(transport.last.httpMethod, "POST")
        XCTAssertEqual(transport.last.url?.path, "/api/groups/g1/charges")
        let expected: NSDictionary = [
            "type": "fine", "description": "Te laat", "amountCents": 200, "memberIds": ["m1", "m2"], "date": "2026-11-02",
        ]
        XCTAssertEqual(try transport.lastJSON(), expected)
    }

    func testEditsTheFineList() async throws {
        transport.body = groupJSON
        _ = try await client().addFine(groupId: "g1", name: "Eigen goal", amountCents: 250)
        XCTAssertEqual(transport.last.httpMethod, "POST")
        XCTAssertEqual(transport.last.url?.path, "/api/groups/g1/fines")
        XCTAssertEqual(try transport.lastJSON(), ["name": "Eigen goal", "amountCents": 250])

        _ = try await client().updateFine(groupId: "g1", fineId: "f1", name: "Te laat", amountCents: 300)
        XCTAssertEqual(transport.last.httpMethod, "PUT")
        XCTAssertEqual(transport.last.url?.path, "/api/groups/g1/fines/f1")
        XCTAssertEqual(try transport.lastJSON(), ["name": "Te laat", "amountCents": 300])

        _ = try await client().removeFine(groupId: "g1", fineId: "f1")
        XCTAssertEqual(transport.last.httpMethod, "DELETE")
        XCTAssertEqual(transport.last.url?.path, "/api/groups/g1/fines/f1")
        XCTAssertNil(transport.last.httpBody)
    }

    func testScanSendsBase64JPEG() async throws {
        transport.body = #"{"merchant":"Café De Zon","date":"2026-11-01","totalCents":null,"items":[{"name":"2× Bier","amountCents":900}]}"#
        let scan = try await client().scanReceipt(jpegData: Data([0xFF, 0xD8, 0xFF]))

        XCTAssertEqual(try transport.lastJSON(), ["imageBase64": "/9j/", "mediaType": "image/jpeg"])
        XCTAssertEqual(scan.merchant, "Café De Zon")
        XCTAssertNil(scan.totalCents)
        XCTAssertEqual(scan.items, [ReceiptScan.Line(name: "2× Bier", amountCents: 900)])
    }

    func testEmptyAnswerIsFineForLogout() async throws {
        transport.status = 204
        transport.body = ""
        try await client().logout()
        XCTAssertEqual(transport.last.url?.path, "/api/auth/logout")
        XCTAssertEqual(transport.last.httpMethod, "POST")
    }

    func testServerErrorCarriesTheDutchMessage() async {
        transport.status = 409
        transport.body = #"{"error":{"code":"member_in_use","message":"Dit lid staat nog in uitgaven en kan niet worden verwijderd."}}"#
        do {
            _ = try await client().removeMember(groupId: "g1", memberId: "m2")
            XCTFail("Expected an error")
        } catch {
            XCTAssertEqual(
                error as? APIError,
                .server(status: 409, code: "member_in_use", message: "Dit lid staat nog in uitgaven en kan niet worden verwijderd.")
            )
            XCTAssertEqual(error.userMessage, "Dit lid staat nog in uitgaven en kan niet worden verwijderd.")
        }
        XCTAssertEqual(expired.value, 0)
    }

    func testUnauthorizedWithTokenEndsTheSession() async {
        transport.status = 401
        transport.body = #"{"error":{"code":"unauthorized","message":"Log opnieuw in."}}"#
        do {
            _ = try await client().groups()
            XCTFail("Expected an error")
        } catch {
            XCTAssertEqual(error as? APIError, .sessionExpired)
        }
        XCTAssertEqual(expired.value, 1)
    }

    func testWrongPasswordIsNotAnExpiredSession() async {
        transport.status = 401
        transport.body = #"{"error":{"code":"invalid_credentials","message":"E-mailadres of wachtwoord klopt niet."}}"#
        do {
            _ = try await client(token: nil).login(email: "anna@example.nl", password: "fout")
            XCTFail("Expected an error")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "invalid_credentials")
            XCTAssertEqual(error.userMessage, "E-mailadres of wachtwoord klopt niet.")
        }
        XCTAssertEqual(expired.value, 0)
        XCTAssertNil(transport.last.value(forHTTPHeaderField: "Authorization"))
    }

    func testAnswersThatAreNotJSONBecomeInvalidResponse() async {
        transport.status = 502
        transport.body = "<html>Bad gateway</html>"
        do {
            _ = try await client().groups()
            XCTFail("Expected an error")
        } catch {
            XCTAssertEqual(error as? APIError, .invalidResponse)
        }

        transport.status = 200
        do {
            _ = try await client().groups()
            XCTFail("Expected an error")
        } catch {
            XCTAssertEqual(error as? APIError, .invalidResponse)
        }
    }

    func testCancelledRequestsHaveNoMessage() {
        XCTAssertNil(APIError.cancelled.userMessage)
        XCTAssertNil(CancellationError().userMessage)
        XCTAssertNotNil(APIError.offline.userMessage)
    }
}
