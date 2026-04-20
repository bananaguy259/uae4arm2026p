param(
	[string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..") | Select-Object -ExpandProperty Path),
	[string]$RemoteName = "upstream",
	[string]$RemoteUrl = "https://github.com/BlitterStudio/amiberry.git",
	[string]$UpstreamBranch = "master",
	[switch]$Fetch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
	param(
		[Parameter(Mandatory = $true)]
		[string[]]$Arguments,
		[switch]$AllowFailure
	)

	$result = & git @Arguments 2>&1
	if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
		throw "git $($Arguments -join ' ') failed: $result"
	}

	return $result
}

Push-Location $RepoRoot
try {
	$insideWorkTree = Invoke-Git -Arguments @("rev-parse", "--is-inside-work-tree")
	if ($insideWorkTree.Trim() -ne "true") {
		throw "Repository root '$RepoRoot' is not a git work tree."
	}

	$existingUrl = Invoke-Git -Arguments @("remote", "get-url", $RemoteName) -AllowFailure
	if ($LASTEXITCODE -ne 0) {
		Invoke-Git -Arguments @("remote", "add", $RemoteName, $RemoteUrl) | Out-Null
	}
	elseif ($existingUrl.Trim() -ne $RemoteUrl) {
		Invoke-Git -Arguments @("remote", "set-url", $RemoteName, $RemoteUrl) | Out-Null
	}

	if ($Fetch) {
		Invoke-Git -Arguments @("fetch", $RemoteName, "--prune") | Out-Null
	}

	$upstreamRef = "$RemoteName/$UpstreamBranch"
	$localBranch = (Invoke-Git -Arguments @("rev-parse", "--abbrev-ref", "HEAD")).Trim()
	$localHead = (Invoke-Git -Arguments @("rev-parse", "HEAD")).Trim()
	$upstreamHead = (Invoke-Git -Arguments @("rev-parse", $upstreamRef)).Trim()
	$mergeBaseResult = Invoke-Git -Arguments @("merge-base", "HEAD", $upstreamRef) -AllowFailure
	$mergeBase = if ($null -eq $mergeBaseResult) { "" } else { "$mergeBaseResult".Trim() }
	$hasMergeBase = $LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($mergeBase)
	$counts = (Invoke-Git -Arguments @("rev-list", "--left-right", "--count", "HEAD...$upstreamRef")).Trim() -split "\s+"

	if ($counts.Count -ne 2) {
		throw "Unexpected rev-list output: $($counts -join ' ')"
	}

	$result = [PSCustomObject]@{
		comparisonDate = (Get-Date).ToString("yyyy-MM-dd")
		repoRoot = $RepoRoot
		remoteName = $RemoteName
		remoteUrl = (Invoke-Git -Arguments @("remote", "get-url", $RemoteName)).Trim()
		localBranch = $localBranch
		localHead = $localHead
		upstreamRef = $upstreamRef
		upstreamHead = $upstreamHead
		mergeBase = $(if ($hasMergeBase) { $mergeBase } else { $null })
		disconnectedHistories = -not $hasMergeBase
		aheadOfUpstream = [int]$counts[0]
		behindUpstream = [int]$counts[1]
		note = $(if ($hasMergeBase) {
			"Shared history detected; ahead/behind counts are normal fork-style counts."
		} else {
			"No shared history detected; ahead/behind counts are raw disconnected-history counts."
		})
	}

	$result | ConvertTo-Json -Depth 3
}
finally {
	Pop-Location
}