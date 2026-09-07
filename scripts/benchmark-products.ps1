[CmdletBinding()]
param(
    [string]$ElasticsearchUrl = "http://localhost:9200",

    [ValidateRange(1, 10000000)]
    [int]$DocumentCount = 100000,

    [ValidateRange(100, 10000)]
    [int]$BatchSize = 1000,

    [ValidateRange(1, 100000)]
    [int]$Iterations = 200,

    [ValidateRange(0, 10000)]
    [int]$WarmupIterations = 20,

    [ValidatePattern("^[a-z0-9][a-z0-9._-]*$")]
    [string]$IndexName = ("products-perf-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
)

$ErrorActionPreference = "Stop"
$ElasticsearchUrl = $ElasticsearchUrl.TrimEnd("/")

function Invoke-ElasticsearchJson {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("Get", "Post", "Put")]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Path,

        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = "$ElasticsearchUrl/$($Path.TrimStart('/'))"
    }

    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }

    Invoke-RestMethod @parameters
}

function Get-Percentile {
    param(
        [double[]]$SortedValues,
        [double]$Percentile
    )

    $index = [Math]::Max(
        0,
        [Math]::Min(
            $SortedValues.Count - 1,
            [Math]::Ceiling(($Percentile / 100) * $SortedValues.Count) - 1
        )
    )
    $SortedValues[$index]
}

function Measure-ElasticsearchRequest {
    param(
        [Parameter(Mandatory)]
        [string]$Name,

        [Parameter(Mandatory)]
        [ValidateSet("Get", "Post")]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Path,

        [object]$Body
    )

    for ($i = 0; $i -lt $WarmupIterations; $i++) {
        $null = Invoke-ElasticsearchJson -Method $Method -Path $Path -Body $Body
    }

    $clientLatencies = [System.Collections.Generic.List[double]]::new()
    $serverTimes = [System.Collections.Generic.List[double]]::new()

    for ($i = 0; $i -lt $Iterations; $i++) {
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-ElasticsearchJson -Method $Method -Path $Path -Body $Body
        $stopwatch.Stop()

        $clientLatencies.Add($stopwatch.Elapsed.TotalMilliseconds)
        if ($null -ne $response.took) {
            $serverTimes.Add([double]$response.took)
        }
    }

    [double[]]$sorted = $clientLatencies | Sort-Object
    $average = ($clientLatencies | Measure-Object -Average).Average
    $serverAverage = if ($serverTimes.Count -gt 0) {
        [Math]::Round(($serverTimes | Measure-Object -Average).Average, 2)
    } else {
        $null
    }

    [pscustomobject]@{
        Scenario = $Name
        Iterations = $Iterations
        ClientAverageMs = [Math]::Round($average, 2)
        ClientP50Ms = [Math]::Round((Get-Percentile $sorted 50), 2)
        ClientP95Ms = [Math]::Round((Get-Percentile $sorted 95), 2)
        ClientP99Ms = [Math]::Round((Get-Percentile $sorted 99), 2)
        ServerAverageMs = $serverAverage
        SequentialRequestsPerSecond = [Math]::Round((1000 / $average), 2)
    }
}

Write-Host "Checking Elasticsearch at $ElasticsearchUrl ..."
$clusterInfo = Invoke-ElasticsearchJson -Method Get -Path "/"
$health = Invoke-ElasticsearchJson -Method Get -Path "_cluster/health"
if ($health.status -eq "red") {
    throw "Elasticsearch cluster status is red."
}

Write-Host "Creating isolated benchmark index '$IndexName' ..."
$indexDefinition = @{
    settings = @{
        number_of_shards = 1
        number_of_replicas = 0
        refresh_interval = "-1"
    }
    mappings = @{
        properties = @{
            id = @{ type = "keyword" }
            name = @{ type = "text"; analyzer = "standard" }
            description = @{ type = "text"; analyzer = "standard" }
            category = @{ type = "keyword" }
            price = @{ type = "double" }
            stock = @{ type = "integer" }
        }
    }
}
$null = Invoke-ElasticsearchJson -Method Put -Path $IndexName -Body $indexDefinition

$templates = @(
    [pscustomobject]@{ Name = "Wireless Headphones"; Category = "Electronics"; Description = "Noise cancelling bluetooth audio headphones for travel and office" },
    [pscustomobject]@{ Name = "Mechanical Keyboard"; Category = "Electronics"; Description = "Mechanical keyboard with tactile switches and wireless connectivity" },
    [pscustomobject]@{ Name = "Standing Desk"; Category = "Furniture"; Description = "Height adjustable standing desk for a productive home office" },
    [pscustomobject]@{ Name = "Office Chair"; Category = "Furniture"; Description = "Ergonomic office chair with lumbar support and adjustable arms" },
    [pscustomobject]@{ Name = "Espresso Beans"; Category = "Grocery"; Description = "Medium roast Colombian coffee beans for espresso machines" },
    [pscustomobject]@{ Name = "Green Tea"; Category = "Grocery"; Description = "Loose leaf green tea with a light floral taste" },
    [pscustomobject]@{ Name = "Running Shoes"; Category = "Sports"; Description = "Lightweight road running shoes with responsive cushioning" },
    [pscustomobject]@{ Name = "Yoga Mat"; Category = "Sports"; Description = "Non slip yoga mat for training stretching and fitness" },
    [pscustomobject]@{ Name = "Winter Jacket"; Category = "Clothing"; Description = "Water resistant insulated jacket for cold weather" },
    [pscustomobject]@{ Name = "Cotton Shirt"; Category = "Clothing"; Description = "Breathable cotton shirt for casual everyday wear" }
)

Write-Host "Bulk indexing $DocumentCount products in batches of $BatchSize ..."
$indexStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$indexed = 0
$batchNumber = 0

while ($indexed -lt $DocumentCount) {
    $currentBatchSize = [Math]::Min($BatchSize, $DocumentCount - $indexed)
    $builder = [System.Text.StringBuilder]::new()

    for ($offset = 1; $offset -le $currentBatchSize; $offset++) {
        $number = $indexed + $offset
        $template = $templates[($number - 1) % $templates.Count]
        $price = [Math]::Round(10 + (($number * 7919) % 49000) / 100, 2)
        $stock = ($number * 17) % 250

        $action = "{`"index`":{`"_index`":`"$IndexName`",`"_id`":`"perf-$number`"}}"
        $document = [ordered]@{
            id = "perf-$number"
            name = "$($template.Name) Model $number"
            description = "$($template.Description). Synthetic benchmark product number $number."
            category = $template.Category
            price = $price
            stock = $stock
        } | ConvertTo-Json -Compress

        $null = $builder.AppendLine($action)
        $null = $builder.AppendLine($document)
    }

    $bulkResponse = Invoke-RestMethod `
        -Method Post `
        -Uri "$ElasticsearchUrl/_bulk" `
        -ContentType "application/x-ndjson" `
        -Body $builder.ToString()

    if ($bulkResponse.errors) {
        $firstFailure = $bulkResponse.items |
            Where-Object { $null -ne $_.index.error } |
            Select-Object -First 1
        throw "Bulk indexing failed: $($firstFailure.index.error.reason)"
    }

    $indexed += $currentBatchSize
    $batchNumber++
    if (($batchNumber % 10 -eq 0) -or ($indexed -eq $DocumentCount)) {
        Write-Host "  Indexed $indexed / $DocumentCount"
    }
}

$indexStopwatch.Stop()

Write-Host "Enabling the normal one-second refresh and making documents searchable ..."
$null = Invoke-ElasticsearchJson -Method Put -Path "$IndexName/_settings" -Body @{
    index = @{ refresh_interval = "1s" }
}
$null = Invoke-ElasticsearchJson -Method Post -Path "$IndexName/_refresh"
$count = Invoke-ElasticsearchJson -Method Get -Path "$IndexName/_count"

$indexingSeconds = [Math]::Round($indexStopwatch.Elapsed.TotalSeconds, 2)
$indexingRate = [Math]::Round($DocumentCount / $indexStopwatch.Elapsed.TotalSeconds, 2)

$searchScenarios = @(
    @{
        Name = "Exact document by _id"
        Method = "Get"
        Path = "$IndexName/_doc/perf-$([Math]::Max(1, [Math]::Floor($DocumentCount / 2)))"
        Body = $null
    },
    @{
        Name = "Full-text: headphones"
        Method = "Post"
        Path = "$IndexName/_search?filter_path=took,hits.total,hits.hits._id,hits.hits._score"
        Body = @{
            size = 20
            track_total_hits = $true
            query = @{
                multi_match = @{
                    query = "headphones"
                    fields = @("name^2", "description")
                }
            }
        }
    },
    @{
        Name = "Text + category filter"
        Method = "Post"
        Path = "$IndexName/_search?filter_path=took,hits.total,hits.hits._id,hits.hits._score"
        Body = @{
            size = 20
            track_total_hits = $true
            query = @{
                bool = @{
                    must = @(
                        @{
                            multi_match = @{
                                query = "noise cancelling headphones"
                                fields = @("name^2", "description")
                            }
                        }
                    )
                    filter = @(
                        @{ term = @{ category = "Electronics" } }
                    )
                }
            }
        }
    },
    @{
        Name = "Exact category filter"
        Method = "Post"
        Path = "$IndexName/_search?filter_path=took,hits.total,hits.hits._id"
        Body = @{
            size = 20
            track_total_hits = $true
            query = @{ term = @{ category = "Furniture" } }
        }
    },
    @{
        Name = "Full-text miss"
        Method = "Post"
        Path = "$IndexName/_search?filter_path=took,hits.total,hits.hits._id"
        Body = @{
            size = 20
            track_total_hits = $true
            query = @{ match = @{ description = "term-that-does-not-exist-xyz" } }
        }
    }
)

Write-Host "Running $WarmupIterations warmups and $Iterations measured requests per scenario ..."
$results = foreach ($scenario in $searchScenarios) {
    Measure-ElasticsearchRequest `
        -Name $scenario.Name `
        -Method $scenario.Method `
        -Path $scenario.Path `
        -Body $scenario.Body
}

$outputDirectory = Join-Path $PSScriptRoot "..\build\performance"
$null = New-Item -ItemType Directory -Force -Path $outputDirectory
$resultFile = Join-Path $outputDirectory "$IndexName.json"

$report = [ordered]@{
    Timestamp = (Get-Date).ToString("o")
    ElasticsearchUrl = $ElasticsearchUrl
    ElasticsearchVersion = $clusterInfo.version.number
    ClusterStatus = $health.status
    IndexName = $IndexName
    DocumentCount = [long]$count.count
    BatchSize = $BatchSize
    IndexingSeconds = $indexingSeconds
    IndexingDocumentsPerSecond = $indexingRate
    WarmupIterations = $WarmupIterations
    MeasuredIterationsPerScenario = $Iterations
    SearchResults = $results
}

$report | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 $resultFile

Write-Host ""
Write-Host "Indexing: $($count.count) documents in $indexingSeconds s ($indexingRate docs/s)"
$results | Format-Table -AutoSize
Write-Host "JSON report: $((Resolve-Path $resultFile).Path)"
Write-Host "Benchmark index was kept. Cleanup when finished:"
Write-Host "  Invoke-RestMethod -Method Delete $ElasticsearchUrl/$IndexName"
