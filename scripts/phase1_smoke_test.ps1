param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$ErrorActionPreference = "Stop"

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-ResponseText {
    param($Response)

    if ($Response.Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Response.Content)
    }

    return [string]$Response.Content
}

function Invoke-Status {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [string]$ContentType,
        [string]$Body
    )

    try {
        $parameters = @{
            UseBasicParsing = $true
            Method = $Method
            Uri = $Uri
            Headers = $Headers
        }
        if ($ContentType) {
            $parameters.ContentType = $ContentType
        }
        if ($Body) {
            $parameters.Body = $Body
        }
        return [int](Invoke-WebRequest @parameters).StatusCode
    }
    catch [System.Net.WebException] {
        return [int]$_.Exception.Response.StatusCode
    }
}

$login = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/auth/login" `
    -ContentType "application/json" `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.token)" }
$testName = "phase1-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$collection = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/v1/collections" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body (@{ name = $testName; description = "Phase 1 smoke test" } | ConvertTo-Json)

$metadataFile = Join-Path $env:TEMP "kv-metadata-$testName.json"

try {
    @{
        collectionId = $collection.id
        title = "Phase 1 Test Document"
        description = "Version workflow verification"
        product = "KV"
        revision = "A"
        tags = "phase1,test"
    } | ConvertTo-Json -Compress | Set-Content -LiteralPath $metadataFile -NoNewline

    $v1 = (Resolve-Path "backend/document-service/src/test/resources/fixtures/version-one.txt").Path
    $uploadJson = & curl.exe -sS -X POST "$BaseUrl/api/v1/documents/upload" `
        -H "Authorization: Bearer $($login.token)" `
        -F "metadata=@$metadataFile;type=application/json" `
        -F "file=@$v1;type=text/plain"
    Assert-True ($LASTEXITCODE -eq 0) "Document upload failed"
    $upload = $uploadJson | ConvertFrom-Json
    Assert-True ($null -ne $upload.documentId) "Upload response omitted documentId: $uploadJson"

    $documentId = $upload.documentId
    $download1 = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri "$BaseUrl/api/v1/documents/$documentId/download" `
        -Headers $headers
    Assert-True ((Get-ResponseText $download1) -match "version one") "Current document download did not return version one"

    $v2 = (Resolve-Path "backend/document-service/src/test/resources/fixtures/version-two.txt").Path
    $versionJson = & curl.exe -sS -X POST "$BaseUrl/api/v1/documents/$documentId/versions" `
        -H "Authorization: Bearer $($login.token)" `
        -F "file=@$v2;type=text/plain"
    Assert-True ($LASTEXITCODE -eq 0) "Version upload failed"
    $version = $versionJson | ConvertFrom-Json
    Assert-True ($version.versionNumber -eq 2) "Expected version 2, received: $versionJson"

    $history = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions" `
        -Headers $headers
    Assert-True ($history.Count -eq 2) "Expected two versions, received $($history.Count)"
    $currentVersions = @($history | Where-Object { $_.isCurrent -eq $true })
    Assert-True ($currentVersions.Count -eq 1) "Expected exactly one current version"
    Assert-True ($history[0].uploadedBy -eq $Username) "Uploader attribution is incorrect"

    $historical = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions/1/download" `
        -Headers $headers
    $current = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions/2/download" `
        -Headers $headers
    Assert-True ((Get-ResponseText $historical) -match "version one") "Historical download did not return version one"
    Assert-True ((Get-ResponseText $current) -match "version two") "Version 2 download did not return version two"

    Invoke-RestMethod `
        -Method Put `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions/1/set-current" `
        -Headers $headers | Out-Null
    $selected = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions/current" `
        -Headers $headers
    Assert-True ($selected.versionNumber -eq 1) "Set-current did not select version one"
    Invoke-RestMethod `
        -Method Put `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions/2/set-current" `
        -Headers $headers | Out-Null

    Invoke-RestMethod `
        -Method Put `
        -Uri "$BaseUrl/api/v1/documents/$documentId" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{ status = "ARCHIVED" } | ConvertTo-Json) | Out-Null
    $archived = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/documents/$documentId" `
        -Headers $headers
    Assert-True ($archived.status -eq "ARCHIVED") "Document archive failed"
    $archivedDownloadStatus = Invoke-Status `
        -Method Get `
        -Uri "$BaseUrl/api/v1/documents/$documentId/versions/1/download" `
        -Headers $headers
    Assert-True ($archivedDownloadStatus -eq 400) "Archived version download should be rejected"

    Invoke-RestMethod `
        -Method Put `
        -Uri "$BaseUrl/api/v1/documents/$documentId" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{ status = "ACTIVE" } | ConvertTo-Json) | Out-Null

    $suffix = [guid]::NewGuid().ToString("N").Substring(0, 8)
    $viewerName = "viewer-$suffix"
    $contributorName = "contrib-$suffix"
    $testPassword = "Phase1Test!42"
    $viewer = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/v1/users" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{
            username = $viewerName
            email = "$viewerName@example.test"
            password = $testPassword
            firstName = "Phase"
            lastName = "Viewer"
            status = "ACTIVE"
        } | ConvertTo-Json)
    $contributor = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/v1/users" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body (@{
            username = $contributorName
            email = "$contributorName@example.test"
            password = $testPassword
            firstName = "Phase"
            lastName = "Contributor"
            status = "ACTIVE"
        } | ConvertTo-Json)

    $roleSql = "DELETE FROM user_roles WHERE user_id = $($contributor.id); " +
        "INSERT INTO user_roles (user_id, role_id) " +
        "SELECT $($contributor.id), id FROM roles WHERE name = 'CONTRIBUTOR';"
    & docker.exe exec knowledgevault-db psql `
        -U postgres `
        -d knowledgevault `
        -v ON_ERROR_STOP=1 `
        -c $roleSql | Out-Null
    Assert-True ($LASTEXITCODE -eq 0) "Could not assign the contributor test role"

    $viewerLogin = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -ContentType "application/json" `
        -Body (@{ username = $viewerName; password = $testPassword } | ConvertTo-Json)
    $contributorLogin = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -ContentType "application/json" `
        -Body (@{ username = $contributorName; password = $testPassword } | ConvertTo-Json)
    $viewerHeaders = @{ Authorization = "Bearer $($viewerLogin.token)" }
    $contributorHeaders = @{ Authorization = "Bearer $($contributorLogin.token)" }

    Assert-True ((Invoke-Status Get "$BaseUrl/api/v1/collections" $viewerHeaders) -eq 200) `
        "VIEWER should be able to list collections"
    Assert-True ((Invoke-Status Post "$BaseUrl/api/v1/collections" $viewerHeaders "application/json" `
        (@{ name = "viewer-denied-$suffix"; description = "must fail" } | ConvertTo-Json)) -eq 403) `
        "VIEWER should not be able to create collections"
    Assert-True ((Invoke-Status Post "$BaseUrl/api/v1/collections" $contributorHeaders "application/json" `
        (@{ name = "contributor-$suffix"; description = "role acceptance test" } | ConvertTo-Json)) -eq 201) `
        "CONTRIBUTOR should be able to create collections"
    Assert-True ((Invoke-Status Delete "$BaseUrl/api/v1/documents/$documentId" $contributorHeaders) -eq 403) `
        "CONTRIBUTOR should not be able to delete documents"

    $pagedCollections = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/collections?page=0&size=1" `
        -Headers $headers
    Assert-True ($pagedCollections.collections.Count -eq 1) "Collection pagination did not enforce page size"

    [pscustomobject]@{
        documentId = $documentId
        versions = $history.Count
        currentVersion = $version.versionNumber
        uploader = $history[0].uploadedBy
        archiveRestore = "passed"
        roleEnforcement = "passed"
        pagination = "passed"
    } | Format-List
}
finally {
    if (Test-Path -LiteralPath $metadataFile) {
        Remove-Item -LiteralPath $metadataFile -Force
    }
}
